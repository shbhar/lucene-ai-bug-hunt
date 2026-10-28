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
 * With the default settings, build() commits the new suggestions before it opens the
 * SearcherManager on them. If build() throws after that commit, the catch block rolls back (which
 * cannot undo the commit) and sets writer = null, so refresh() becomes a no-op: lookups keep
 * serving the old suggestions while the directory holds the new ones, and the next add() silently
 * switches to the new ones.
 */
public class TestAnalyzingInfixSuggesterFailedBuild extends LuceneTestCase {

  /** Throws one IOException while build() opens the SearcherManager on the new index. */
  private static final class FailOpeningNewSearcher extends MockDirectoryWrapper.Failure {
    @Override
    public void eval(MockDirectoryWrapper dir) throws IOException {
      if (doFail
          && callStackContains(SearcherManager.class, "<init>")
          && callStackContains(MockDirectoryWrapper.class, "openInput")) {
        doFail = false;
        throw new IOException("simulated I/O error while opening the new searcher");
      }
    }
  }

  /** Default settings: a build() that throws after its commit. */
  public void testFailedBuildAfterCommit() throws Exception {
    Path path = createTempDir();
    MockDirectoryWrapper dir = newMockFSDirectory(path);
    FailOpeningNewSearcher failure = new FailOpeningNewSearcher();
    dir.failOn(failure);
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(dir, analyzer);
    suggester.build(suggestions("old apple"));
    assertEquals(List.of("old apple"), lookupA(suggester));

    failure.setDoFail();
    expectThrows(IOException.class, () -> suggester.build(suggestions("new avocado")));
    suggester.refresh();
    List<String> served = lookupA(suggester);
    List<String> stored = storedSuggestions(path, analyzer);

    suggester.add(new BytesRef("another apricot"), null, 5, null);
    suggester.refresh();
    List<String> afterAdd = lookupA(suggester);
    suggester.close();
    analyzer.close();

    String views = "served=" + served + " stored=" + stored + " afterAdd=" + afterAdd;
    assertEquals(
        "after build() threw and refresh(), served must equal stored: " + views, stored, served);
    assertEquals(
        "add() + refresh() must add one suggestion: " + views,
        plus(served, "another apricot"),
        afterAdd);
  }

  /** No I/O fault: a rebuild fails on one over-long entry after an add() that was not refreshed. */
  public void testFailedRebuildHidesCommittedAdd() throws Exception {
    Path path = createTempDir();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(newFSDirectory(path), analyzer);
    suggester.build(suggestions("old apple"));
    suggester.add(new BytesRef("added apricot"), null, 5, null); // not refreshed yet

    String overLong = "a" + "x".repeat(40_000); // longer than IndexWriter's 32766-byte term limit
    expectThrows(
        IllegalArgumentException.class,
        () -> suggester.build(suggestions("new avocado", overLong)));
    suggester.refresh();
    List<String> served = lookupA(suggester);
    List<String> stored = storedSuggestions(path, analyzer);
    suggester.close();
    analyzer.close();

    assertEquals("after build() threw and refresh(), served must equal stored", stored, served);
  }

  /** Control: nothing is committed inside build(), so the same fault leaves a consistent view. */
  public void testControlNoCommitInsideBuild() throws Exception {
    Path path = createTempDir();
    MockDirectoryWrapper dir = newMockFSDirectory(path);
    FailOpeningNewSearcher failure = new FailOpeningNewSearcher();
    dir.failOn(failure);
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    // commitOnBuild=false, closeIndexWriterOnBuild=false
    AnalyzingInfixSuggester suggester =
        new AnalyzingInfixSuggester(dir, analyzer, analyzer, 4, false, true, true, false);
    suggester.build(suggestions("old apple"));
    suggester.commit();

    failure.setDoFail();
    expectThrows(IOException.class, () -> suggester.build(suggestions("new avocado")));
    suggester.refresh();
    List<String> served = lookupA(suggester);
    List<String> stored = storedSuggestions(path, analyzer);

    suggester.add(new BytesRef("another apricot"), null, 5, null);
    suggester.refresh();
    List<String> afterAdd = lookupA(suggester);
    suggester.close();
    analyzer.close();

    assertEquals(List.of("old apple"), served);
    assertEquals(stored, served);
    assertEquals(plus(served, "another apricot"), afterAdd);
  }

  /** What a new suggester opened on the same directory serves. */
  private static List<String> storedSuggestions(Path path, Analyzer analyzer) throws IOException {
    try (AnalyzingInfixSuggester reopened =
        new AnalyzingInfixSuggester(newFSDirectory(path), analyzer)) {
      return lookupA(reopened);
    }
  }

  private static List<String> lookupA(AnalyzingInfixSuggester suggester) throws IOException {
    List<String> keys = new ArrayList<>();
    for (LookupResult result : suggester.lookup("a", 10, true, false)) {
      keys.add(result.key.toString());
    }
    keys.sort(null);
    return keys;
  }

  private static List<String> plus(List<String> keys, String key) {
    List<String> out = new ArrayList<>(keys);
    out.add(key);
    out.sort(null);
    return out;
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
}
