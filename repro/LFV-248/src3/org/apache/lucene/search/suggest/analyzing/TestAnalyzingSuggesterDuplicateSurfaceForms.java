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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * AnalyzingSuggester.build counts every sorted entry of an analyzed form toward
 * maxSurfaceFormsPerAnalyzedForm, including repeated copies of a surface form it then drops as
 * duplicates; distinct surface forms beyond that count are discarded.
 */
public class TestAnalyzingSuggesterDuplicateSurfaceForms extends LuceneTestCase {

  private static InputIterator iter(String[] surfaces, long[] weights) {
    Iterator<Integer> it = java.util.stream.IntStream.range(0, surfaces.length).iterator();
    return new InputIterator() {
      int cur;

      @Override
      public BytesRef next() {
        if (it.hasNext() == false) {
          return null;
        }
        cur = it.next();
        return new BytesRef(surfaces[cur]);
      }

      @Override
      public long weight() {
        return weights[cur];
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

  /** maxSurfaceFormsPerAnalyzedForm = 2: one duplicate copy of "a" costs "A" its slot. */
  public void testDuplicateCopyTakesSlot() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true); // lowercasing
    Directory tempDir = newDirectory();
    AnalyzingSuggester suggester = new AnalyzingSuggester(tempDir, "suggest", a, a, 0, 2, -1, true);
    suggester.build(iter(new String[] {"a", "a", "A"}, new long[] {10, 5, 3}));
    List<LookupResult> results = suggester.lookup("a", false, 5);
    // two distinct surface forms ("a", "A") share the analyzed form "a": both fit in 2
    assertEquals("[a/10, A/3]", results.toString());
    IOUtils.close(a, tempDir);
  }

  /** Default settings (256): 300 copies of "apple" hide the distinct surface form "Apple". */
  public void testDefaultLimitWithRepeatedEntries() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    Directory tempDir = newDirectory();
    AnalyzingSuggester suggester = new AnalyzingSuggester(tempDir, "suggest", a);
    List<String> s = new ArrayList<>();
    List<Long> w = new ArrayList<>();
    for (int i = 0; i < 300; i++) {
      s.add("apple"); // e.g. one entry per document of a DocumentDictionary
      w.add(100L);
    }
    s.add("Apple");
    w.add(1L);
    suggester.build(iter(s.toArray(new String[0]), w.stream().mapToLong(Long::longValue).toArray()));
    assertEquals("[apple/100, Apple/1]", suggester.lookup("app", false, 5).toString());
    IOUtils.close(a, tempDir);
  }

  /** Control: without the duplicate copy both surface forms are kept. */
  public void testControlNoDuplicate() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    Directory tempDir = newDirectory();
    AnalyzingSuggester suggester = new AnalyzingSuggester(tempDir, "suggest", a, a, 0, 2, -1, true);
    suggester.build(iter(new String[] {"a", "A"}, new long[] {10, 3}));
    assertEquals("[a/10, A/3]", suggester.lookup("a", false, 5).toString());
    IOUtils.close(a, tempDir);
  }
}
