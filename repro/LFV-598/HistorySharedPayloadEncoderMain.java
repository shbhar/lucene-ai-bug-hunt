/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dpfhistory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.payloads.AbstractEncoder;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.BytesRef;

/**
 * Version-portable check (Lucene 4.0 to main) of whether DelimitedPayloadTokenFilterFactory hands one instance of a
 * custom PayloadEncoder class to every filter it creates.
 *
 * <p>The factory is reached by reflection only because its construction API changed over the years (no-arg
 * constructor plus init(Map) before 4.3, a Map constructor since then) and ResourceLoader moved packages in 9.0.
 * Everything else uses public API that is unchanged since 4.0. No test framework is needed.
 *
 * <p>Exit codes: 1 = affected (a filter reads another filter's payload); 0 = not affected, or the factory does not
 * exist in this release (printed as NOT_APPLICABLE); 2 = a control failed or setup error (the check is not valid).
 */
public final class HistorySharedPayloadEncoderMain {

  static final String FACTORY = "org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilterFactory";

  /** Allocation-free float encoder: returns the same BytesRef on every call. Public with a no-arg constructor. */
  public static final class ReusingFloatEncoder extends AbstractEncoder {
    private final BytesRef scratch = new BytesRef(new byte[4]);

    public ReusingFloatEncoder() {}

    @Override
    public BytesRef encode(char[] buffer, int offset, int length) {
      int bits = Float.floatToIntBits(Float.parseFloat(new String(buffer, offset, length)));
      scratch.bytes[0] = (byte) (bits >>> 24);
      scratch.bytes[1] = (byte) (bits >>> 16);
      scratch.bytes[2] = (byte) (bits >>> 8);
      scratch.bytes[3] = (byte) bits;
      scratch.offset = 0;
      scratch.length = 4;
      return scratch;
    }
  }

  /** A token stream with exactly one token. */
  static final class OneToken extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final String text;
    private boolean done;

    OneToken(String text) {
      this.text = text;
    }

    @Override
    public boolean incrementToken() {
      if (done) return false;
      clearAttributes();
      term.setEmpty().append(text);
      done = true;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      done = false;
    }
  }

  static float decode(BytesRef p) {
    int o = p.offset;
    int bits =
        ((p.bytes[o] & 0xFF) << 24)
            | ((p.bytes[o + 1] & 0xFF) << 16)
            | ((p.bytes[o + 2] & 0xFF) << 8)
            | (p.bytes[o + 3] & 0xFF);
    return Float.intBitsToFloat(bits);
  }

  static Class<?> firstClass(String... names) throws ClassNotFoundException {
    for (String n : names) {
      try {
        return Class.forName(n);
      } catch (ClassNotFoundException e) {
        // try the next one
      }
    }
    throw new ClassNotFoundException(String.join(" or ", names));
  }

  /** Builds and informs a factory the way an analysis chain does. */
  static Object newFactory(Class<?> fc, String encoder) throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("encoder", encoder);
    args.put("delimiter", "|");
    Object factory;
    Constructor<?> mapCtor = null;
    try {
      mapCtor = fc.getConstructor(Map.class);
    } catch (NoSuchMethodException e) {
      // before 4.3
    }
    if (mapCtor != null) {
      factory = mapCtor.newInstance(args);
    } else {
      factory = fc.getConstructor().newInstance();
      fc.getMethod("init", Map.class).invoke(factory, args);
    }
    Class<?> rl =
        firstClass(
            "org.apache.lucene.util.ClasspathResourceLoader",
            "org.apache.lucene.analysis.util.ClasspathResourceLoader");
    Object loader =
        rl.getConstructor(ClassLoader.class)
            .newInstance(HistorySharedPayloadEncoderMain.class.getClassLoader());
    Method inform = null;
    for (Method m : fc.getMethods()) {
      if (m.getName().equals("inform")
          && m.getParameterTypes().length == 1
          && m.getParameterTypes()[0].isInstance(loader)) {
        inform = m;
      }
    }
    if (inform == null) throw new IllegalStateException("no inform(ResourceLoader) on " + fc);
    inform.invoke(factory, loader);
    return factory;
  }

  static TokenStream create(Object factory, TokenStream in) throws Exception {
    return (TokenStream) factory.getClass().getMethod("create", TokenStream.class).invoke(factory, in);
  }

  /** Informational only: the encoder object a DelimitedPayloadTokenFilter holds, or null if not readable. */
  static Object encoderOf(TokenStream filter) {
    try {
      Field f = DelimitedPayloadTokenFilter.class.getDeclaredField("encoder");
      f.setAccessible(true);
      return f.get(filter);
    } catch (Throwable t) {
      return null;
    }
  }

  interface FilterSource {
    TokenStream newFilter(String text) throws Exception;
  }

  /**
   * Two filters from one source, one thread: filter A reads token "x|1.0"; before its payload is read (IndexWriter
   * reads it right after incrementToken()), filter B reads "y|2.0". Returns the payload filter A then sees.
   */
  static float twoFilters(FilterSource src) throws Exception {
    TokenStream a = src.newFilter("x|1.0");
    TokenStream b = src.newFilter("y|2.0");
    PayloadAttribute pa = a.addAttribute(PayloadAttribute.class);
    PayloadAttribute pb = b.addAttribute(PayloadAttribute.class);
    a.reset();
    if (!a.incrementToken()) throw new IllegalStateException("A has no token");
    b.reset();
    if (!b.incrementToken()) throw new IllegalStateException("B has no token");
    if (decode(pb.getPayload()) != 2.0f) throw new IllegalStateException("B payload " + decode(pb.getPayload()));
    float got = decode(pa.getPayload());
    a.end();
    b.end();
    a.close();
    b.close();
    return got;
  }

  /**
   * The same with two threads, each building its own filter from the source (as an Analyzer's per-thread components
   * do). Thread A reads its token, then waits while thread B analyses; returns the payload thread A then sees.
   */
  static float twoThreads(FilterSource src) throws Exception {
    CountDownLatch aHasToken = new CountDownLatch(1);
    CountDownLatch bDone = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicReference<Float> aPayload = new AtomicReference<>();
    Thread ta =
        new Thread(
            () -> {
              try {
                TokenStream ts = src.newFilter("x|1.0");
                PayloadAttribute pay = ts.addAttribute(PayloadAttribute.class);
                ts.reset();
                if (!ts.incrementToken()) throw new IllegalStateException("A has no token");
                aHasToken.countDown();
                if (!bDone.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("B timed out");
                aPayload.set(decode(pay.getPayload()));
                ts.end();
                ts.close();
              } catch (Throwable t) {
                failure.set(t);
                aHasToken.countDown();
              }
            });
    Thread tb =
        new Thread(
            () -> {
              try {
                if (!aHasToken.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("A timed out");
                TokenStream ts = src.newFilter("y|2.0");
                PayloadAttribute pay = ts.addAttribute(PayloadAttribute.class);
                ts.reset();
                if (!ts.incrementToken()) throw new IllegalStateException("B has no token");
                if (decode(pay.getPayload()) != 2.0f) throw new IllegalStateException("B payload wrong");
                ts.end();
                ts.close();
              } catch (Throwable t) {
                failure.set(t);
              } finally {
                bDone.countDown();
              }
            });
    ta.start();
    tb.start();
    ta.join();
    tb.join();
    if (failure.get() != null) throw new RuntimeException(failure.get());
    return aPayload.get();
  }

  public static void main(String[] args) throws Exception {
    Class<?> fc;
    try {
      fc = Class.forName(FACTORY);
    } catch (ClassNotFoundException e) {
      System.out.println(
          "filter class present: "
              + DelimitedPayloadTokenFilter.class.getName()
              + "; factory class absent: "
              + FACTORY);
      System.out.println("RESULT: NOT_APPLICABLE (this release has no DelimitedPayloadTokenFilterFactory)");
      System.exit(0);
      return;
    }

    // Controls: must give each filter its own payload on every version, or the check is not valid.
    final Object floatFactory = newFactory(fc, "float");
    float cFloat = twoFilters(text -> create(floatFactory, new OneToken(text)));
    float cCtor = twoFilters(text -> new DelimitedPayloadTokenFilter(new OneToken(text), '|', new ReusingFloatEncoder()));
    float cCtorThreads =
        twoThreads(text -> new DelimitedPayloadTokenFilter(new OneToken(text), '|', new ReusingFloatEncoder()));
    System.out.println("control built-in encoder=float via factory, two filters: A sees " + cFloat + " (want 1.0)");
    System.out.println("control custom encoder via public constructor (one per filter), two filters: A sees " + cCtor);
    System.out.println("control custom encoder via public constructor, two threads: A sees " + cCtorThreads);
    if (cFloat != 1.0f || cCtor != 1.0f || cCtorThreads != 1.0f) {
      System.out.println("RESULT: INVALID (a control failed)");
      System.exit(2);
      return;
    }

    // The case under test: a custom encoder class named in the factory arguments.
    final Object customFactory = newFactory(fc, ReusingFloatEncoder.class.getName());
    TokenStream f1 = create(customFactory, new OneToken("p|1.0"));
    TokenStream f2 = create(customFactory, new OneToken("q|2.0"));
    Object e1 = encoderOf(f1), e2 = encoderOf(f2);
    String identity =
        (e1 == null || e2 == null)
            ? "unknown (field not readable)"
            : (e1 == e2 ? "the SAME instance" : "different instances");
    f1.close();
    f2.close();
    System.out.println("custom encoder: two filters from one factory hold " + identity);
    float bugFilters = twoFilters(text -> create(customFactory, new OneToken(text)));
    float bugThreads = twoThreads(text -> create(customFactory, new OneToken(text)));
    System.out.println("custom encoder via factory, two filters, one thread: A sees " + bugFilters + " (want 1.0)");
    System.out.println("custom encoder via factory, two threads: A sees " + bugThreads + " (want 1.0)");
    if (bugFilters != 1.0f || bugThreads != 1.0f) {
      System.out.println("RESULT: AFFECTED (a filter's token carries another filter's payload)");
      System.exit(1);
    } else {
      System.out.println("RESULT: NOT_AFFECTED");
      System.exit(0);
    }
  }
}
