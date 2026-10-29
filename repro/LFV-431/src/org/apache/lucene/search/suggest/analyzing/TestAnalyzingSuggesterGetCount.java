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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

public class TestAnalyzingSuggesterGetCount extends LuceneTestCase {

  public void testSynonymDoesNotInflateCount() throws Exception {
    SynonymMap.Builder builder = new SynonymMap.Builder(true);
    builder.add(new CharsRef("fast"), new CharsRef("quick"), true);
    SynonymMap synonyms = builder.build();
    try (Analyzer analyzer =
            new Analyzer() {
              @Override
              protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = new WhitespaceTokenizer();
                return new TokenStreamComponents(
                    tokenizer, new SynonymGraphFilter(tokenizer, synonyms, true));
              }
            };
        Directory dir = newDirectory()) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(dir, "suggest", analyzer);
      suggester.build(new PlainTextDictionary(new StringReader("fast")));
      // one entry, two analyzed paths (fast, quick): expected 1, actual 2
      assertEquals("number of entries the lookup was built with", 1, suggester.getCount());
    }
  }
}
