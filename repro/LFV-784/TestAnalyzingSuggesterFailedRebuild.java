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
package org.apache.lucene.search.suggest.analyzing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.ByteBuffersDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.ThreadInterruptedException;

/**
 * A rebuild of AnalyzingSuggester / FuzzySuggester whose input has the other payload setting must
 * not change how the previous suggestions are decoded, whether that rebuild throws or is still
 * reading its input. FileDictionary takes the payload setting from the first line of the file.
 */
public class TestAnalyzingSuggesterFailedRebuild extends LuceneTestCase {

  // previous build with payloads; the rebuild without payloads fails while reading its input
  public void testFailedRebuildWithoutPayloads() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new MockAnalyzer(random())) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(dir, "suggest", a);
      suggester.build(dict("foo\t10\tp1\n"));
      assertEquals("[foo (weight 10, payload p1)]", lookup(suggester, "fo"));

      expectThrows(IOException.class, () -> suggester.build(dictFailingAfter("bar\t3\n")));
      // main: [foo<U+001F>p1 (weight 10, payload null)]
      assertEquals("[foo (weight 10, payload p1)]", lookup(suggester, "fo"));

      AnalyzingSuggester reloaded = new AnalyzingSuggester(dir, "suggest", a);
      storeAndLoad(suggester, reloaded);
      assertEquals("[foo (weight 10, payload p1)]", lookup(reloaded, "fo"));
    }
  }

  // previous build without payloads; the rebuild with payloads rejects its second entry
  public void testFailedRebuildWithPayloads() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new MockAnalyzer(random())) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(dir, "suggest", a);
      suggester.build(dict("foo\t10\n"));
      assertEquals("[foo (weight 10, payload null)]", lookup(suggester, "fo"));

      // U+001F is reserved in surface forms when payloads are enabled
      expectThrows(
          IllegalArgumentException.class,
          () -> suggester.build(dict("bar\t3\tq\nb\u001Fz\t2\tr\n")));
      // main: AssertionError with assertions on; [ (weight 10, payload foo)] with assertions off
      assertEquals("[foo (weight 10, payload null)]", lookup(suggester, "fo"));
    }
  }

  // FuzzySuggester inherits build(); the rebuild fails on a weight that cannot be encoded
  public void testFuzzySuggesterFailedRebuild() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new MockAnalyzer(random())) {
      FuzzySuggester suggester = new FuzzySuggester(dir, "suggest", a);
      suggester.build(dict("foobar\t10\tp1\n"));
      assertEquals("[foobar (weight 10, payload p1)]", lookup(suggester, "foob"));

      expectThrows(
          UnsupportedOperationException.class, () -> suggester.build(dict("bar\t3\nbaz\t-1\n")));
      // main: [foobar<U+001F>p1 (weight 10, payload null)]
      assertEquals("[foobar (weight 10, payload p1)]", lookup(suggester, "foob"));
    }
  }

  // control: the same failure with the same payload setting leaves the suggester intact
  public void testFailedRebuildWithSamePayloadSetting() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new MockAnalyzer(random())) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(dir, "suggest", a);
      suggester.build(dict("foo\t10\tp1\n"));
      expectThrows(IOException.class, () -> suggester.build(dictFailingAfter("bar\t3\tq\n")));
      assertEquals("[foo (weight 10, payload p1)]", lookup(suggester, "fo"));
    }
  }

  // no failure: a lookup while a rebuild without payloads is still reading its input
  public void testLookupDuringRebuildWithoutPayloads() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new MockAnalyzer(random())) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(dir, "suggest", a);
      suggester.build(dict("foo\t10\tp1\n"));

      CountDownLatch rebuildIsReading = new CountDownLatch(1);
      CountDownLatch lookupDone = new CountDownLatch(1);
      InputIterator slow =
          new PausingIterator(dict("bar\t3\n").getEntryIterator(), rebuildIsReading, lookupDone);
      AtomicReference<Throwable> buildError = new AtomicReference<>();
      Thread rebuilder =
          new Thread(
              () -> {
                try {
                  suggester.build(slow);
                } catch (Throwable t) {
                  buildError.set(t);
                }
              });
      rebuilder.start();
      String during;
      try {
        rebuildIsReading.await();
        during = lookup(suggester, "fo");
      } finally {
        lookupDone.countDown();
        rebuilder.join();
      }
      assertNull(buildError.get());
      // main: [foo<U+001F>p1 (weight 10, payload null)]
      assertEquals("[foo (weight 10, payload p1)]", during);
      assertEquals("[bar (weight 3, payload null)]", lookup(suggester, "ba"));
    }
  }

  private static FileDictionary dict(String content) {
    return new FileDictionary(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  /** A well-formed file whose read fails after {@code content}, like a truncated download. */
  private static FileDictionary dictFailingAfter(String content) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    InputStream in =
        new InputStream() {
          int pos;

          @Override
          public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xFF;
          }

          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            if (pos >= bytes.length) {
              throw new IOException("simulated read failure");
            }
            int n = Math.min(len, bytes.length - pos);
            System.arraycopy(bytes, pos, b, off, n);
            pos += n;
            return n;
          }
        };
    return new FileDictionary(in);
  }

  private static String lookup(Lookup suggester, String prefix) throws IOException {
    List<String> out = new ArrayList<>();
    for (LookupResult r : suggester.lookup(prefix, false, 5)) {
      String payload = r.payload == null ? "null" : escape(r.payload.utf8ToString());
      out.add(escape(r.key.toString()) + " (weight " + r.value + ", payload " + payload + ")");
    }
    return out.toString();
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      sb.append(c < 0x20 ? String.format(Locale.ROOT, "<U+%04X>", (int) c) : String.valueOf(c));
    }
    return sb.toString();
  }

  private static void storeAndLoad(Lookup from, Lookup into) throws IOException {
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    assertTrue(from.store(out));
    assertTrue(into.load(new ByteBuffersDataInput(out.toBufferList())));
  }

  /** Delegates to an InputIterator, but blocks in its first next() until a lookup is done. */
  private static final class PausingIterator implements InputIterator {
    private final InputIterator in;
    private final CountDownLatch reading;
    private final CountDownLatch resume;
    private boolean first = true;

    PausingIterator(InputIterator in, CountDownLatch reading, CountDownLatch resume) {
      this.in = in;
      this.reading = reading;
      this.resume = resume;
    }

    @Override
    public BytesRef next() throws IOException {
      if (first) {
        first = false;
        reading.countDown();
        try {
          resume.await();
        } catch (InterruptedException e) {
          throw new ThreadInterruptedException(e);
        }
      }
      return in.next();
    }

    @Override
    public long weight() {
      return in.weight();
    }

    @Override
    public BytesRef payload() {
      return in.payload();
    }

    @Override
    public boolean hasPayloads() {
      return in.hasPayloads();
    }

    @Override
    public Set<BytesRef> contexts() {
      return in.contexts();
    }

    @Override
    public boolean hasContexts() {
      return in.hasContexts();
    }
  }
}
