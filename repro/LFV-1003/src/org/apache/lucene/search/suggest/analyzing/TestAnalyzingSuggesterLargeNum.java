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

import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

public class TestAnalyzingSuggesterLargeNum extends LuceneTestCase {

  public void testLargeNumReturnsAllMatches() throws Exception {
    SynonymMap.Builder synonyms = new SynonymMap.Builder(true);
    synonyms.add(new CharsRef("ab"), new CharsRef("x"), true);
    synonyms.add(new CharsRef("ab"), new CharsRef("y"), true);
    SynonymMap map = synonyms.build();
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new SynonymGraphFilter(t, map, true));
          }
        };
    AnalyzingSuggester suggester =
        new AnalyzingSuggester(new ByteBuffersDirectory(), "suggest", analyzer);
    suggester.build(new FileDictionary(new StringReader("ab\t1\nac\t2\nad\t3")).getEntryIterator());

    List<String> keys =
        suggester.lookup("a", false, 1431655766).stream().map(r -> r.key.toString()).toList();
    // expected: all 3 matches; actual: [ad, ac], as the queue depth 1431655766 * 3 paths wraps to 2
    assertEquals(List.of("ad", "ac", "ab"), keys);
  }
}
