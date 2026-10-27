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
package org.apache.lucene.search.suggest.tst;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.ByteBuffersDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.ThreadInterruptedException;

/**
 * TSTLookup.build replaces its tree with an empty one before it reads the input, and resets its
 * count only after the input was sorted. A rebuild that throws, or a lookup made while a rebuild
 * is still reading its input, sees an empty tree next to the previous getCount().
 */
public class TestTSTLookupRebuild extends LuceneTestCase {

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

  private static String describe(Lookup lookup) throws IOException {
    StringBuilder sb = new StringBuilder("count=" + lookup.getCount() + " fo->");
    for (LookupResult r : lookup.lookup("fo", false, 5)) {
      sb.append('[').append(r.key).append('/').append(r.value).append(']');
    }
    return sb.toString();
  }

  /** The previous build's state, or an empty lookup; never a count that disagrees with lookups. */
  private static void assertPreviousOrEmpty(String when, Lookup lookup) throws IOException {
    String state = describe(lookup);
    if (state.equals("count=0 fo->") == false) {
      assertEquals(when, "count=2 fo->[foo/10]", state);
    }
  }

  public void testFailedRebuild() throws Exception {
    try (Directory tempDir = newDirectory()) {
      TSTLookup lookup = new TSTLookup(tempDir, "suggest");
      lookup.build(dict("foo\t10\nbar\t5\n"));
      assertEquals("count=2 fo->[foo/10]", describe(lookup));

      // reading the new dictionary fails after its first line, while the input is being sorted
      expectThrows(IOException.class, () -> lookup.build(dictFailingAfterFirstLine("baz\t3\n")));

      // on main: count=2 fo-> (the tree is empty, the count is the previous one)
      assertPreviousOrEmpty("after a failed build", lookup);

      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      assertTrue(lookup.store(out));
      TSTLookup reloaded = new TSTLookup(tempDir, "suggest");
      assertTrue(reloaded.load(new ByteBuffersDataInput(out.toBufferList())));
      assertPreviousOrEmpty("after store/load of the failed-build state", reloaded);
    }
  }

  /**
   * The check TestAnalyzingSuggester, TestFSTCompletion and TestWFSTCompletion make with
   * SuggestRebuildTestUtil#testLookupsDuringReBuild: the previous suggestions stay available while
   * a rebuild is reading its input. Latches make the interleaving deterministic.
   */
  public void testLookupDuringRebuild() throws Exception {
    try (Directory tempDir = newDirectory()) {
      TSTLookup lookup = new TSTLookup(tempDir, "suggest");
      lookup.build(dict("foo\t10\nbar\t5\n"));
      assertEquals("count=2 fo->[foo/10]", describe(lookup));

      CountDownLatch rebuildIsReading = new CountDownLatch(1);
      CountDownLatch checked = new CountDownLatch(1);
      InputIterator source = dict("foo\t10\nbar\t5\nfoe\t1\n").getEntryIterator();
      InputIterator slow =
          new InputIterator() {
            boolean first = true;

            @Override
            public BytesRef next() throws IOException {
              if (first) {
                first = false;
                rebuildIsReading.countDown();
                try {
                  checked.await();
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
                  lookup.build(slow);
                } catch (Throwable t) {
                  buildError.set(t);
                }
              });
      rebuilder.start();
      String during;
      try {
        rebuildIsReading.await();
        during = describe(lookup);
      } finally {
        checked.countDown();
        rebuilder.join();
      }
      assertNull(buildError.get());
      // on main: count=2 fo-> while the rebuild is reading its input
      assertEquals("during the rebuild", "count=2 fo->[foo/10]", during);
      // the rebuild itself completes normally (TSTLookup lists completions alphabetically here)
      assertEquals("count=3 fo->[foe/1][foo/10]", describe(lookup));
    }
  }
}
