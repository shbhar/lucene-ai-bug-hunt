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

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQueryBuilderLeadingStopWordSynonym extends LuceneTestCase {

  // Expected: SynonymQuery(dogs, dog), as for "dogs"; actual: MultiPhraseQuery "(dogs dog)".
  public void testLeadingStopWordBeforeSynonym() {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, true);
            CharArraySet stopWords = new CharArraySet(List.of("the"), false);
            return new TokenStreamComponents(
                tokenizer, new MockSynonymFilter(new StopFilter(tokenizer, stopWords)));
          }
        };
    SynonymQuery expected =
        new SynonymQuery.Builder("field")
            .addTerm(new Term("field", "dogs"))
            .addTerm(new Term("field", "dog"))
            .build();
    assertEquals(expected, new QueryBuilder(analyzer).createPhraseQuery("field", "the dogs"));
  }
}
