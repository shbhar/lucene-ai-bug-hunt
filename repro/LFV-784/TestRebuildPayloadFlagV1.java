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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * Independent verifier repro (public API only): a rebuild of AnalyzingSuggester / FuzzySuggester
 * whose input has a different hasPayloads() value than the previous build must not change how the
 * previous suggestions are returned if that rebuild fails, nor while that rebuild is still reading
 * its input (the latter is the property SuggestRebuildTestUtil#testLookupsDuringReBuild checks for
 * AnalyzingSuggester, with an unchanged payload flag).
 */
public class TestRebuildPayloadFlagV1 extends LuceneTestCase {

  private static FileDictionary dict(String text) {
    return new FileDictionary(
        new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "\t");
  }

  private static String describe(List<LookupResult> results) {
    StringBuilder sb = new StringBuilder();
    for (LookupResult r : results) {
      sb.append('[')
          .append(r.key.toString().replace("\u001f", "<US>"))
          .append(" w=")
          .append(r.value)
          .append(" payload=")
          .append(r.payload == null ? "null" : r.payload.utf8ToString())
          .append(']');
    }
    return sb.toString();
  }

  /** An application InputIterator (no payloads) whose source fails after the first entry. */
  private static final class FailingNoPayloadIterator implements InputIterator {
    private int i = 0;

    @Override
    public BytesRef next() throws IOException {
      if (i++ == 0) {
        return new BytesRef("bar");
      }
      throw new IOException("simulated read error from the suggestion source");
    }

    @Override
    public long weight() {
      return 5;
    }

    @Override
    public BytesRef payload() {
      return null;
    }

    @Override
    public boolean hasPayloads() {
      return false;
    }

    @Override
    public Set<BytesRef> contexts() {
      return null;
    }

    @Override
    public boolean hasContexts() {
      return false;
    }
  }

  private Analyzer keywordAnalyzer() {
    return new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
  }

  /** Previous build WITH payloads; failing rebuild (IOException from the source) WITHOUT. */
  public void testIOExceptionDuringRebuildWithoutPayloads() throws Exception {
    Directory tempDir = newDirectory();
    Analyzer a = keywordAnalyzer();
    AnalyzingSuggester s = new AnalyzingSuggester(tempDir, "suggest", a);
    s.build(dict("foo\t10\tp1\n"));
    String before = describe(s.lookup("fo", false, 5));
    assertEquals("[foo w=10 payload=p1]", before);
    long countBefore = s.getCount();

    expectThrows(IOException.class, () -> s.build(new FailingNoPayloadIterator()));

    assertEquals(countBefore, s.getCount());
    assertEquals("lookup after failed rebuild", before, describe(s.lookup("fo", false, 5)));
    IOUtils.close(a, tempDir);
  }

  /** Previous build WITH payloads; rebuild WITHOUT payloads rejected by encodeWeight (w=-1). */
  public void testRejectedWeightRebuildWithoutPayloads() throws Exception {
    Directory tempDir = newDirectory();
    Analyzer a = keywordAnalyzer();
    AnalyzingSuggester s = new AnalyzingSuggester(tempDir, "suggest", a);
    s.build(dict("foo\t10\tp1\n"));
    String before = describe(s.lookup("fo", false, 5));
    assertEquals("[foo w=10 payload=p1]", before);

    Throwable t = expectThrowsAnyOf(
        List.of(UnsupportedOperationException.class, IllegalArgumentException.class),
        () -> s.build(dict("bar\t5\nbaz\t-1\n")));
    assertNotNull(t);

    String after = describe(s.lookup("fo", false, 5));
    // store/load of the instance after the failure persists whatever state it has
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    assertTrue(s.store(out));
    AnalyzingSuggester loaded = new AnalyzingSuggester(tempDir, "suggest2", a);
    assertTrue(loaded.load(new ByteBuffersDataInput(out.toBufferList())));
    String afterLoad = describe(loaded.lookup("fo", false, 5));

    assertEquals(
        "lookup after failed rebuild | after store+load",
        before + " | " + before,
        after + " | " + afterLoad);
    IOUtils.close(a, tempDir);
  }

  /** Previous build WITHOUT payloads; rebuild WITH payloads rejected by encodeWeight (w=-1). */
  public void testRejectedWeightRebuildWithPayloads() throws Exception {
    Directory tempDir = newDirectory();
    Analyzer a = keywordAnalyzer();
    AnalyzingSuggester s = new AnalyzingSuggester(tempDir, "suggest", a);
    s.build(dict("foo\t10\n"));
    String before = describe(s.lookup("fo", false, 5));
    assertEquals("[foo w=10 payload=null]", before);

    expectThrowsAnyOf(
        List.of(UnsupportedOperationException.class, IllegalArgumentException.class),
        () -> s.build(dict("bar\t5\tp2\nbaz\t-1\tp3\n")));

    assertEquals("lookup after failed rebuild", before, describe(s.lookup("fo", false, 5)));
    IOUtils.close(a, tempDir);
  }

  /** Same as the first case on FuzzySuggester (inherits build()). */
  public void testFuzzySuggesterRejectedWeightRebuild() throws Exception {
    Directory tempDir = newDirectory();
    Analyzer a = keywordAnalyzer();
    FuzzySuggester s = new FuzzySuggester(tempDir, "suggest", a);
    s.build(dict("foobar\t10\tp1\n"));
    String before = describe(s.lookup("foob", false, 5));
    assertEquals("[foobar w=10 payload=p1]", before);

    expectThrowsAnyOf(
        List.of(UnsupportedOperationException.class, IllegalArgumentException.class),
        () -> s.build(dict("bar\t5\nbaz\t-1\n")));

    assertEquals("lookup after failed rebuild", before, describe(s.lookup("foob", false, 5)));
    IOUtils.close(a, tempDir);
  }

  /**
   * No failure: while a successful rebuild WITHOUT payloads is still reading its input, a lookup
   * should still return the previous build's suggestions (as SuggestRebuildTestUtil checks).
   */
  public void testLookupDuringRebuildWithDifferentPayloadFlag() throws Exception {
    Directory tempDir = newDirectory();
    Analyzer a = keywordAnalyzer();
    AnalyzingSuggester s = new AnalyzingSuggester(tempDir, "suggest", a);
    s.build(dict("foo\t10\tp1\n"));
    String before = describe(s.lookup("fo", false, 5));
    assertEquals("[foo w=10 payload=p1]", before);

    CountDownLatch inNext = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    InputIterator slow =
        new InputIterator() {
          int i = 0;

          @Override
          public BytesRef next() throws IOException {
            if (i == 0) {
              inNext.countDown();
              try {
                release.await();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
            return i++ == 0 ? new BytesRef("bar") : null;
          }

          @Override
          public long weight() {
            return 5;
          }

          @Override
          public BytesRef payload() {
            return null;
          }

          @Override
          public boolean hasPayloads() {
            return false;
          }

          @Override
          public Set<BytesRef> contexts() {
            return null;
          }

          @Override
          public boolean hasContexts() {
            return false;
          }
        };
    AtomicReference<Throwable> err = new AtomicReference<>();
    Thread rebuilder =
        new Thread(
            () -> {
              try {
                s.build(slow);
              } catch (Throwable t) {
                err.set(t);
              }
            });
    rebuilder.start();
    String during;
    try {
      assertTrue(inNext.await(30, TimeUnit.SECONDS));
      during = describe(s.lookup("fo", false, 5));
    } finally {
      release.countDown();
      rebuilder.join();
    }
    assertNull(err.get());
    assertEquals("[bar w=5 payload=null]", describe(s.lookup("ba", false, 5)));
    assertEquals("lookup during rebuild", before, during);
    IOUtils.close(a, tempDir);
  }

  /** Control: same payload flag in both builds (WITH payloads); the failed rebuild is harmless. */
  public void testControlSameFlagWithPayloads() throws Exception {
    Directory tempDir = newDirectory();
    Analyzer a = keywordAnalyzer();
    AnalyzingSuggester s = new AnalyzingSuggester(tempDir, "suggest", a);
    s.build(dict("foo\t10\tp1\n"));
    String before = describe(s.lookup("fo", false, 5));
    expectThrowsAnyOf(
        List.of(UnsupportedOperationException.class, IllegalArgumentException.class),
        () -> s.build(dict("bar\t5\tp2\nbaz\t-1\tp3\n")));
    assertEquals("control", before, describe(s.lookup("fo", false, 5)));
    IOUtils.close(a, tempDir);
  }

  /** Control: same payload flag in both builds (WITHOUT payloads); the failed rebuild is harmless. */
  public void testControlSameFlagWithoutPayloads() throws Exception {
    Directory tempDir = newDirectory();
    Analyzer a = keywordAnalyzer();
    AnalyzingSuggester s = new AnalyzingSuggester(tempDir, "suggest", a);
    s.build(dict("foo\t10\n"));
    String before = describe(s.lookup("fo", false, 5));
    expectThrowsAnyOf(
        List.of(UnsupportedOperationException.class, IllegalArgumentException.class),
        () -> s.build(dict("bar\t5\nbaz\t-1\n")));
    assertEquals("control", before, describe(s.lookup("fo", false, 5)));
    IOUtils.close(a, tempDir);
  }
}
