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

import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;

/**
 * Lookup#getCount is documented as "the number of entries the lookup was built with". Since the
 * iterator rewrite of LUCENE-6365, AnalyzingSuggester (and FuzzySuggester) increments its count
 * once per analyzed path instead of once per entry, so an analyzer that produces a token graph
 * (here a synonym) inflates the count.
 */
public class TestAnalyzingSuggesterGetCount extends LuceneTestCase {

  private static Analyzer synonymAnalyzer() throws Exception {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    b.add(new CharsRef("fast"), new CharsRef("quick"), true); // fast -> {fast, quick}
    final SynonymMap map = b.build();
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(t, new SynonymGraphFilter(t, map, true));
      }
    };
  }

  private static InputIterator entries(String... keys) {
    return new InputIterator() {
      int i = -1;

      @Override
      public BytesRef next() {
        return ++i < keys.length ? new BytesRef(keys[i]) : null;
      }

      @Override
      public long weight() {
        return i + 1;
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

  private void check(Lookup suggester) throws Exception {
    suggester.build(entries("fast car", "red car"));
    // both analyzed forms of "fast car" work, but it is still one entry
    assertEquals(1, suggester.lookup("quick", false, 5).size());
    assertEquals(1, suggester.lookup("fast", false, 5).size());
    assertEquals("number of entries the lookup was built with", 2, suggester.getCount());
  }

  public void testAnalyzingSuggester() throws Exception {
    try (Analyzer a = synonymAnalyzer();
        Directory tempDir = newDirectory()) {
      check(new AnalyzingSuggester(tempDir, "suggest", a));
    }
  }

  public void testFuzzySuggester() throws Exception {
    try (Analyzer a = synonymAnalyzer();
        Directory tempDir = newDirectory()) {
      check(new FuzzySuggester(tempDir, "suggest", a));
    }
  }
}
