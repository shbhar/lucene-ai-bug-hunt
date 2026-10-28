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
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
 * A build() that throws must not leave AnalyzingSuggester/FuzzySuggester answering lookups from the
 * previous FST decoded with the failed input's hasPayloads flag. FileDictionary decides hasPayloads
 * from the first line of the file, so the flag can change between two rebuilds from the same kind
 * of source.
 */
public class TestFailedBuildPayloadFlag extends LuceneTestCase {

  private static FileDictionary dict(String content) {
    return new FileDictionary(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * A dictionary file whose read fails after the first line, like a truncated network or disk
   * read. Every line it delivers is well-formed.
   */
  private static FileDictionary dictFailingAfterFirstLine(String firstLine) {
    byte[] bytes = firstLine.getBytes(StandardCharsets.UTF_8);
    InputStream in =
        new InputStream() {
          int pos;

          @Override
          public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : one[0] & 0xFF;
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

  /** Lookups must answer as before the (failed or unfinished) build, or not at all. */
  private static void assertPreviousOrEmpty(
      String when, List<LookupResult> results, String key, long weight, String payload) {
    if (results.isEmpty()) {
      return;
    }
    assertEquals(results.toString(), 1, results.size());
    LookupResult r = results.get(0);
    String got =
        escape(r.key.toString())
            + "/"
            + r.value
            + " payload="
            + (r.payload == null ? "null" : escape(r.payload.utf8ToString()));
    String want = key + "/" + weight + " payload=" + (payload == null ? "null" : payload);
    assertEquals(when, want, got);
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c < 0x20) {
        sb.append(String.format(java.util.Locale.ROOT, "<U+%04X>", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static Lookup storeAndLoad(Lookup from, Lookup into) throws Exception {
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    assertTrue(from.store(out));
    assertTrue(into.load(new ByteBuffersDataInput(out.toBufferList())));
    return into;
  }

  // previous build had payloads, the failed build had none
  public void testFailedBuildWithoutPayloadsAfterBuildWithPayloads() throws Exception {
    try (Directory tempDir = newDirectory(); Analyzer a = new MockAnalyzer(random())) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(tempDir, "suggest", a);
      suggester.build(dict("foo\t10\tp1\n"));
      assertPreviousOrEmpty("after the first build", suggester.lookup("fo", false, 5), "foo", 10, "p1");

      // the new file has no payload on its first line; reading it fails after that line
      expectThrows(IOException.class, () -> suggester.build(dictFailingAfterFirstLine("bar\t3\n")));

      List<LookupResult> after = suggester.lookup("fo", false, 5);
      // on main: [foo<U+001F>p1/10] -- the payload is glued to the suggestion text and payload is null
      assertPreviousOrEmpty("after a failed build", after, "foo", 10, "p1");

      Lookup reloaded = storeAndLoad(suggester, new AnalyzingSuggester(tempDir, "suggest", a));
      assertPreviousOrEmpty("after store/load of the failed-build state", reloaded.lookup("fo", false, 5), "foo", 10, "p1");
    }
  }

  // previous build had no payloads, the failed build had payloads
  public void testFailedBuildWithPayloadsAfterBuildWithoutPayloads() throws Exception {
    try (Directory tempDir = newDirectory(); Analyzer a = new MockAnalyzer(random())) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(tempDir, "suggest", a);
      suggester.build(dict("foo\t10\n"));
      assertPreviousOrEmpty("after the first build", suggester.lookup("fo", false, 5), "foo", 10, null);

      // the new file has payloads; AnalyzingSuggester rejects its second entry because U+001F is
      // reserved in payload-enabled surface forms
      expectThrows(
          IllegalArgumentException.class,
          () -> suggester.build(dict("bar\t3\tq\nb\u001Fz\t2\tr\n")));

      // on main: AssertionError with -ea; with -da a suggestion with empty text and payload "foo"
      assertPreviousOrEmpty("after a failed build", suggester.lookup("fo", false, 5), "foo", 10, null);
    }
  }

  // FuzzySuggester inherits build(); same failure with the suggester's own input check
  public void testFuzzySuggesterRejectedEntry() throws Exception {
    try (Directory tempDir = newDirectory(); Analyzer a = new MockAnalyzer(random())) {
      FuzzySuggester suggester = new FuzzySuggester(tempDir, "suggest", a);
      suggester.build(dict("foobar\t10\tp1\n"));
      assertPreviousOrEmpty("after the first build", suggester.lookup("foob", false, 5), "foobar", 10, "p1");

      // weights must fit in [0, Integer.MAX_VALUE]: the build throws UnsupportedOperationException
      expectThrows(
          UnsupportedOperationException.class,
          () -> suggester.build(dict("bar\t3\nbaz\t-1\n")));

      assertPreviousOrEmpty("after a failed build", suggester.lookup("foob", false, 5), "foobar", 10, "p1");
    }
  }

  /**
   * The same state is visible without any failure to a lookup that runs while a rebuild is still
   * reading its input, the situation TestAnalyzingSuggester#testLookupsDuringReBuild checks for
   * inputs with the same payload flag. Latches make the interleaving deterministic.
   */
  public void testLookupDuringRebuildWithDifferentPayloadFlag() throws Exception {
    try (Directory tempDir = newDirectory(); Analyzer a = new MockAnalyzer(random())) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(tempDir, "suggest", a);
      suggester.build(dict("foo\t10\tp1\n"));
      assertPreviousOrEmpty("after the first build", suggester.lookup("fo", false, 5), "foo", 10, "p1");

      CountDownLatch rebuildIsReading = new CountDownLatch(1);
      CountDownLatch lookupDone = new CountDownLatch(1);
      InputIterator source = dict("bar\t3\n").getEntryIterator();
      InputIterator slow =
          new InputIterator() {
            boolean first = true;

            @Override
            public BytesRef next() throws IOException {
              if (first) {
                first = false;
                rebuildIsReading.countDown();
                try {
                  lookupDone.await();
                } catch (InterruptedException e) {
                  throw new ThreadInterruptedException(e);
                }
              }
              return source.next();
            }

            @Override
            public long weight() {
              return source.weight();
            }

            @Override
            public BytesRef payload() {
              return source.payload();
            }

            @Override
            public boolean hasPayloads() {
              return source.hasPayloads();
            }

            @Override
            public Set<BytesRef> contexts() {
              return source.contexts();
            }

            @Override
            public boolean hasContexts() {
              return source.hasContexts();
            }
          };
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
      List<LookupResult> during;
      try {
        rebuildIsReading.await();
        during = suggester.lookup("fo", false, 5);
      } finally {
        lookupDone.countDown();
        rebuilder.join();
      }
      assertNull(buildError.get());
      // on main: [foo<U+001F>p1/10 payload=null] while the rebuild is reading its input
      assertPreviousOrEmpty("during the rebuild", during, "foo", 10, "p1");
    }
  }
}
