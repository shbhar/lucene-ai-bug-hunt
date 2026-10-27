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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * build() commits the new suggestions (closeIndexWriterOnBuild defaults to true) before it opens
 * the SearcherManager on them. If opening that reader fails, build() rolls the writer back and
 * rethrows, but the commit is already durable: the instance keeps serving the old suggestions,
 * refresh() is a no-op, and the next add() or a reopen switches to the "failed" build and drops
 * the old suggestions.
 */
public class TestInfixFailedBuildAfterCommit extends LuceneTestCase {

  /** One IOException while the SearcherManager over the newly built index opens its reader. */
  private static final class FailOpeningNewReader extends MockDirectoryWrapper.Failure {
    @Override
    public void eval(MockDirectoryWrapper dir) throws IOException {
      if (doFail
          && callStackContains(SearcherManager.class, "<init>")
          && callStackContains(MockDirectoryWrapper.class, "openInput")) {
        doFail = false;
        throw new IOException("simulated I/O error while opening the new reader");
      }
    }
  }

  private static InputIterator suggestions(String... texts) {
    Iterator<String> it = List.of(texts).iterator();
    return new InputIterator() {
      @Override
      public BytesRef next() {
        return it.hasNext() ? new BytesRef(it.next()) : null;
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
  }

  private static List<String> lookupA(AnalyzingInfixSuggester s) throws IOException {
    List<String> out = new ArrayList<>();
    for (LookupResult r : s.lookup("a", 10, true, false)) {
      out.add(r.key.toString());
    }
    out.sort(null);
    return out;
  }

  /** The default configuration: closeIndexWriterOnBuild=true, commitOnBuild=false. */
  public void testAddAfterFailedBuildResurrectsIt() throws Exception {
    Path path = createTempDir();
    MockDirectoryWrapper dir = newMockFSDirectory(path);
    FailOpeningNewReader failure = new FailOpeningNewReader();
    dir.failOn(failure);
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(dir, analyzer);
    suggester.build(suggestions("old apple"));
    assertEquals(List.of("old apple"), lookupA(suggester));

    failure.setDoFail();
    expectThrows(IOException.class, () -> suggester.build(suggestions("new avocado")));
    suggester.refresh();
    List<String> afterFailedBuild = lookupA(suggester); // [old apple] on main

    // Adding one suggestion must not replace the suggestions that lookups already return.
    suggester.add(new BytesRef("another apricot"), null, 5, null);
    suggester.refresh();
    List<String> afterAdd = lookupA(suggester);
    suggester.close();
    analyzer.close();
    List<String> expected = new ArrayList<>(afterFailedBuild);
    expected.add("another apricot");
    expected.sort(null);
    assertEquals(
        "after a failed build(), add() + refresh() must add exactly one suggestion to the served ones",
        expected,
        afterAdd);
  }

  /** What the instance serves after the failed build and refresh() must be what a reopen sees. */
  public void testLiveViewAfterFailedBuildDiffersFromReopenedIndex() throws Exception {
    for (boolean commitOnBuild : new boolean[] {false, true}) {
      Path path = createTempDir();
      MockDirectoryWrapper dir = newMockFSDirectory(path);
      FailOpeningNewReader failure = new FailOpeningNewReader();
      dir.failOn(failure);
      Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
      AnalyzingInfixSuggester suggester =
          new AnalyzingInfixSuggester(dir, analyzer, analyzer, 4, commitOnBuild, true, false, true);
      suggester.build(suggestions("old apple"));
      failure.setDoFail();
      expectThrows(IOException.class, () -> suggester.build(suggestions("new avocado")));
      suggester.refresh();
      List<String> live = lookupA(suggester);
      suggester.close();

      AnalyzingInfixSuggester reopened =
          new AnalyzingInfixSuggester(newMockFSDirectory(path), analyzer);
      List<String> persisted = lookupA(reopened);
      reopened.close();
      analyzer.close();
      assertEquals(
          "commitOnBuild=" + commitOnBuild + ": suggestions served after the failed build vs. reopened index",
          live,
          persisted);
    }
  }
  /** BlendedInfixSuggester inherits build() and always closes its writer on build. */
  public void testBlendedInfixSuggesterAddAfterFailedBuild() throws Exception {
    Path path = createTempDir();
    MockDirectoryWrapper dir = newMockFSDirectory(path);
    FailOpeningNewReader failure = new FailOpeningNewReader();
    dir.failOn(failure);
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    BlendedInfixSuggester suggester = new BlendedInfixSuggester(dir, analyzer);
    suggester.build(suggestions("old apple"));
    failure.setDoFail();
    expectThrows(IOException.class, () -> suggester.build(suggestions("new avocado")));
    suggester.refresh();
    List<String> afterFailedBuild = lookupA(suggester); // [old apple] on main
    suggester.add(new BytesRef("another apricot"), null, 5, null);
    suggester.refresh();
    List<String> afterAdd = lookupA(suggester);
    suggester.close();
    analyzer.close();
    List<String> expected = new ArrayList<>(afterFailedBuild);
    expected.add("another apricot");
    expected.sort(null);
    assertEquals(
        "after a failed build(), add() + refresh() must add exactly one suggestion to the served ones",
        expected,
        afterAdd);
  }
  private static InputIterator failingAfterFirst(String text) {
    InputIterator first = suggestions(text);
    return new InputIterator() {
      boolean returned;

      @Override
      public BytesRef next() throws IOException {
        if (returned) {
          throw new IOException("simulated read error in the suggestion source");
        }
        returned = true;
        return first.next();
      }

      @Override
      public long weight() {
        return first.weight();
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

  /**
   * No Directory fault: the suggestion source of a rebuild throws. build() has already closed (and
   * so committed) the writer holding an added suggestion, and afterwards refresh() cannot show it.
   */
  public void testRefreshAfterFailedRebuildCannotShowAddedSuggestion() throws Exception {
    Path path = createTempDir();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(newFSDirectory(path), analyzer);
    suggester.build(suggestions("old apple"));
    suggester.add(new BytesRef("added apricot"), null, 5, null);
    expectThrows(IOException.class, () -> suggester.build(failingAfterFirst("new avocado")));
    suggester.refresh();
    List<String> live = lookupA(suggester);
    suggester.close();
    AnalyzingInfixSuggester reopened = new AnalyzingInfixSuggester(newFSDirectory(path), analyzer);
    List<String> persisted = lookupA(reopened);
    reopened.close();
    analyzer.close();
    assertEquals("served after the failed rebuild and refresh() vs. persisted", persisted, live);
  }
}
