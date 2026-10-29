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
package org.apache.lucene.util;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.boost.DelimitedBoostTokenFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQueryBuilderMultiPhraseBoost extends LuceneTestCase {

  public void testPhraseBoostKeptWhenAWordHasASynonym() throws Exception {
    SynonymMap.Builder synonyms = new SynonymMap.Builder();
    synonyms.add(new CharsRef("car"), new CharsRef("automobile"), true);
    SynonymMap map = synonyms.build();
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                tokenizer,
                new SynonymGraphFilter(new DelimitedBoostTokenFilter(tokenizer, '|'), map, true));
          }
        };
    Query q = new QueryBuilder(analyzer).createPhraseQuery("body", "fast|0.5 car");
    // "fast|0.5 tiger" gives (body:"fast tiger")^0.5; expected boost 0.5 here too, got 1.0
    assertEquals(q.toString(), 0.5f, q instanceof BoostQuery bq ? bq.getBoost() : 1f, 0f);
  }
}
