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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.search.suggest.analyzing.FuzzySuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.BytesRef;

/**
 * History probe, InputIterator API with a temp Directory (6.0.0 .. main).
 *
 * <p>A build() that throws must not leave AnalyzingSuggester/FuzzySuggester decoding the previous
 * FST with the failed input's hasPayloads flag. Bug cases change the payload flag between the good
 * build and the failed build; control cases keep it. Exits non-zero (AssertionError) when a bug
 * case shows the wrong state; exits 0 when every case matches the previous build (or is empty).
 */
public class PayloadFlagHistoryProbe {

  // ---- the only API-generation-specific part: how a suggester is constructed ----
  static Directory tempDir;

  static AnalyzingSuggester analyzing(Analyzer a) throws IOException {
    return new AnalyzingSuggester(tempDir, "suggest", a);
  }

  static AnalyzingSuggester fuzzy(Analyzer a) throws IOException {
    return new FuzzySuggester(tempDir, "suggest", a);
  }

  // ---- input: fixed entries; optionally next() throws IOException after failAfter entries ----
  static final class Entries implements InputIterator {
    private final String[] keys;
    private final long[] weights;
    private final String[] payloads; // null: this input has no payloads
    private final int failAfter; // < 0: never fail
    private int pos = -1;

    Entries(String[] keys, long[] weights, String[] payloads, int failAfter) {
      this.keys = keys;
      this.weights = weights;
      this.payloads = payloads;
      this.failAfter = failAfter;
    }

    public BytesRef next() throws IOException {
      if (failAfter >= 0 && pos + 1 >= failAfter) {
        throw new IOException("simulated read failure");
      }
      pos++;
      return pos < keys.length ? new BytesRef(keys[pos]) : null;
    }

    public long weight() {
      return weights[pos];
    }

    public BytesRef payload() {
      return payloads == null ? null : new BytesRef(payloads[pos]);
    }

    public boolean hasPayloads() {
      return payloads != null;
    }

    // present only on some versions; harmless where the interface does not declare them
    public Set<BytesRef> contexts() {
      return null;
    }

    public boolean hasContexts() {
      return false;
    }

    public Comparator<BytesRef> getComparator() {
      return null;
    }
  }

  static Entries withPayloads(String[] k, long[] w, String[] p, int failAfter) {
    return new Entries(k, w, p, failAfter);
  }

  static Entries noPayloads(String[] k, long[] w, int failAfter) {
    return new Entries(k, w, null, failAfter);
  }

  // ---- checking ----
  static final List<String> bugs = new ArrayList<>();
  static final List<String> controlFailures = new ArrayList<>();

  static String escape(CharSequence s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0x20) {
        sb.append(String.format(java.util.Locale.ROOT, "<U+%04X>", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  static String render(List<LookupResult> results) {
    List<String> out = new ArrayList<>();
    for (LookupResult r : results) {
      out.add(
          escape(r.key)
              + "/"
              + r.value
              + " payload="
              + (r.payload == null ? "null" : escape(r.payload.utf8ToString())));
    }
    return out.toString();
  }

  /** previous build's single result, or no result, is correct; anything else is wrong */
  static void check(boolean control, String name, Lookup s, String prefix, String want) {
    String got;
    try {
      got = render(s.lookup(prefix, false, 5));
    } catch (Throwable t) {
      got = "lookup threw " + t;
    }
    boolean ok = got.equals("[]") || got.equals("[" + want + "]");
    System.out.println(
        (ok ? "OK      " : (control ? "CONTROL-FAILED " : "BUG     "))
            + name
            + ": want [] or ["
            + want
            + "], got "
            + got);
    if (!ok) {
      (control ? controlFailures : bugs).add(name + " -> " + got);
    }
  }

  static void expectBuildFails(Lookup s, InputIterator in, Class<? extends Throwable> expected)
      throws Exception {
    try {
      s.build(in);
    } catch (Throwable t) {
      if (expected.isInstance(t)) {
        return;
      }
      throw new AssertionError("build threw " + t + ", expected " + expected.getSimpleName(), t);
    }
    throw new AssertionError("build did not throw " + expected.getSimpleName());
  }

  static Lookup storeAndLoad(Lookup from, Lookup into) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (!from.store(out)) {
      throw new AssertionError("store returned false");
    }
    if (!into.load(new ByteArrayInputStream(out.toByteArray()))) {
      throw new AssertionError("load returned false");
    }
    return into;
  }

  static String[] s(String... v) {
    return v;
  }

  static long[] w(long... v) {
    return v;
  }

  public static void main(String[] args) throws Exception {
    Analyzer a = new KeywordAnalyzer();
    // NIOFSDirectory, not FSDirectory.open: old MMapDirectory static init fails on recent JDKs
    tempDir =
        new NIOFSDirectory(
            Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "probe"));

    // A: previous build with payloads, failed build without (IOException from the input)
    AnalyzingSuggester sa = analyzing(a);
    sa.build(withPayloads(s("foo"), w(10), s("p1"), -1));
    check(true, "A0 sanity after first build", sa, "fo", "foo/10 payload=p1");
    expectBuildFails(sa, noPayloads(s("bar"), w(3), 1), IOException.class);
    check(false, "A1 payloads -> failed build without payloads (IOException)", sa, "fo", "foo/10 payload=p1");
    check(
        false,
        "A2 same state after store()+load()",
        storeAndLoad(sa, analyzing(a)),
        "fo",
        "foo/10 payload=p1");

    // B: previous build without payloads, failed build with payloads (suggester rejects U+001F)
    AnalyzingSuggester sb = analyzing(a);
    sb.build(noPayloads(s("foo"), w(10), -1));
    check(true, "B0 sanity after first build", sb, "fo", "foo/10 payload=null");
    expectBuildFails(
        sb, withPayloads(s("bar", "b\u001Fz"), w(3, 2), s("q", "r"), -1), IllegalArgumentException.class);
    check(false, "B1 no payloads -> failed build with payloads (IAE U+001F)", sb, "fo", "foo/10 payload=null");

    // C: FuzzySuggester, previous build with payloads, failed build without (weight -1)
    AnalyzingSuggester sc = fuzzy(a);
    sc.build(withPayloads(s("foobar"), w(10), s("p1"), -1));
    check(true, "C0 sanity after first build", sc, "foob", "foobar/10 payload=p1");
    expectBuildFails(sc, noPayloads(s("bar", "baz"), w(3, -1), -1), UnsupportedOperationException.class);
    check(false, "C1 FuzzySuggester payloads -> failed build without payloads (weight -1)", sc, "foob", "foobar/10 payload=p1");

    // K1/K2 controls: the failed build has the SAME payload flag as the previous build
    AnalyzingSuggester k1 = analyzing(a);
    k1.build(withPayloads(s("foo"), w(10), s("p1"), -1));
    expectBuildFails(k1, withPayloads(s("bar"), w(3), s("q"), 1), IOException.class);
    check(true, "K1 control payloads -> failed build with payloads", k1, "fo", "foo/10 payload=p1");

    AnalyzingSuggester k2 = analyzing(a);
    k2.build(noPayloads(s("foo"), w(10), -1));
    expectBuildFails(k2, noPayloads(s("bar"), w(3), 1), IOException.class);
    check(true, "K2 control no payloads -> failed build without payloads", k2, "fo", "foo/10 payload=null");

    System.out.println("SUMMARY bug_cases_wrong=" + bugs.size() + "/4 controls_failed=" + controlFailures.size() + "/5");
    if (!controlFailures.isEmpty()) {
      throw new AssertionError("CONTROL FAILED (probe invalid on this version): " + controlFailures);
    }
    if (!bugs.isEmpty()) {
      throw new AssertionError("failed build left the new payload flag on the old FST: " + bugs);
    }
  }
}
