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
package org.apache.lucene.analysis.boost;

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

public class TestStackedTokenBoostRange extends LuceneTestCase {

  public void testSynonymWeightAboveOne() throws Exception {
    SolrSynonymParser parser = new SolrSynonymParser(true, true, new WhitespaceAnalyzer());
    parser.parse(new StringReader("leopard, panthera|1.5"));
    SynonymMap synonyms = parser.build();
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                tokenizer,
                new DelimitedBoostTokenFilter(
                    new SynonymGraphFilter(tokenizer, synonyms, true), '|'));
          }
        };
    // expected: a query, as weight 1.5 on an unstacked token gives a BoostQuery; actual: IAE
    try {
      new QueryBuilder(analyzer).createBooleanQuery("body", "leopard");
    } catch (IllegalArgumentException e) {
      throw new AssertionError("expected a query for synonym weight 1.5, but got " + e, e);
    }
  }
}
