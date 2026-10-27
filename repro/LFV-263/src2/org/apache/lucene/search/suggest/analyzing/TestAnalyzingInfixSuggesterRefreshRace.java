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

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.InfoStream;

/**
 * With the default closeIndexWriterOnBuild=true, the first add() after build() reopens a writer in
 * ensureOpen(): it assigns the field `writer` and only then installs a new SearcherManager. A
 * refresh() from another thread in that window sees writer != null and calls
 * maybeRefreshBlocking() on the SearcherManager left by build(), whose writer is closed:
 * AlreadyClosedException. The window is held open deterministically with the IndexWriter's
 * InfoStream ("flush at getReader" is logged by the new SearcherManager's NRT open, after the
 * writer field was assigned).
 */
public class TestAnalyzingInfixSuggesterRefreshRace extends LuceneTestCase {

  private static InputIterator iter(String text, long weight) {
    Iterator<String> it = List.of(text).iterator();
    return new InputIterator() {
      @Override
      public BytesRef next() {
        return it.hasNext() ? new BytesRef(it.next()) : null;
      }

      @Override
      public long weight() {
        return weight;
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
  }

  public void testRefreshDuringFirstAddAfterBuild() throws Exception {
    final AtomicBoolean armed = new AtomicBoolean(false);
    final CountDownLatch inWindow = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final InfoStream hook =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if ("flush at getReader".equals(message) && armed.compareAndSet(true, false)) {
              inWindow.countDown();
              try {
                // bounded: a fix that makes refresh() wait for the adder must still pass
                release.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return "IW".equals(component);
          }

          @Override
          public void close() {}
        };
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    Directory dir = newDirectory();
    AnalyzingInfixSuggester suggester =
        new AnalyzingInfixSuggester(dir, a, a, 3, false) {
          @Override
          protected IndexWriterConfig getIndexWriterConfig(
              Analyzer indexAnalyzer, IndexWriterConfig.OpenMode openMode) {
            IndexWriterConfig iwc = super.getIndexWriterConfig(indexAnalyzer, openMode);
            iwc.setInfoStream(hook);
            return iwc;
          }
        };
    try {
      suggester.build(iter("lend me your ear", 8)); // closeIndexWriterOnBuild defaults to true
      armed.set(true);
      final AtomicReference<Throwable> addFailure = new AtomicReference<>();
      Thread adder =
          new Thread(
              () -> {
                try {
                  suggester.add(new BytesRef("a penny saved is a penny earned"), null, 10, null);
                } catch (Throwable t) {
                  addFailure.set(t);
                }
              });
      adder.start();
      assertTrue("adder did not reach the window", inWindow.await(60, TimeUnit.SECONDS));
      try {
        // documented way to make additions visible; must not fail because another thread is adding
        suggester.refresh();
      } finally {
        release.countDown();
        adder.join();
      }
      assertNull(addFailure.get());
      suggester.refresh();
      List<LookupResult> results = suggester.lookup("penn", 10, true, false);
      assertEquals(1, results.size());
    } finally {
      release.countDown();
      suggester.close();
      a.close();
    }
  }

  /**
   * Same root cause through build(): a rebuild assigns `writer` before it installs the new
   * SearcherManager, so refresh() during a rebuild refreshes the old SearcherManager, whose writer
   * build() closed. Fails for both values of closeIndexWriterOnBuild.
   */
  public void testRefreshDuringRebuild() throws Exception {
    for (boolean closeOnBuild : new boolean[] {false, true}) {
      Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
      AnalyzingInfixSuggester suggester =
          new AnalyzingInfixSuggester(
              newDirectory(),
              a,
              a,
              3,
              false,
              AnalyzingInfixSuggester.DEFAULT_ALL_TERMS_REQUIRED,
              AnalyzingInfixSuggester.DEFAULT_HIGHLIGHT,
              closeOnBuild);
      try {
        suggester.build(iter("lend me your ear", 8));
        final CountDownLatch inRebuild = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final InputIterator blocking =
            new InputIterator() {
              boolean first = true;

              @Override
              public BytesRef next() {
                if (first == false) {
                  return null;
                }
                first = false;
                inRebuild.countDown();
                try {
                  release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return new BytesRef("a penny saved is a penny earned");
              }

              @Override
              public long weight() {
                return 10;
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
        final AtomicReference<Throwable> buildFailure = new AtomicReference<>();
        Thread rebuilder =
            new Thread(
                () -> {
                  try {
                    suggester.build(blocking);
                  } catch (Throwable t) {
                    buildFailure.set(t);
                  }
                });
        rebuilder.start();
        assertTrue(inRebuild.await(60, TimeUnit.SECONDS));
        try {
          // lookups during a rebuild are supported (testLookupsDuringReBuild); so is refresh()
          assertEquals(1, suggester.lookup("ear", 10, true, false).size());
          suggester.refresh();
        } catch (Throwable t) {
          throw new AssertionError("closeIndexWriterOnBuild=" + closeOnBuild, t);
        } finally {
          release.countDown();
          rebuilder.join();
        }
        assertNull(buildFailure.get());
      } finally {
        suggester.close();
        a.close();
      }
    }
  }

  /** Control: the same sequence without the concurrent refresh works. */
  public void testSequentialControl() throws IOException {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(newDirectory(), a, a, 3, false);
    try {
      suggester.build(iter("lend me your ear", 8));
      suggester.add(new BytesRef("a penny saved is a penny earned"), null, 10, null);
      suggester.refresh();
      assertEquals(1, suggester.lookup("penn", 10, true, false).size());
    } finally {
      suggester.close();
      a.close();
    }
  }
}
