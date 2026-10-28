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
package org.apache.lucene.queryparser.classic;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BoostAttribute;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Token boosts in quoted phrases through the classic QueryParser. MockSynonymFilter adds the
 * single-word synonym "dog" to "dogs"; BoostFastFilter gives the token "fast" a boost of 0.5.
 */
public class TestQueryParserMultiPhraseBoost extends LuceneTestCase {

  /** Boost 0.5 on the token "fast"; every other token keeps the default boost 1. */
  private static final class BoostFastFilter extends TokenFilter {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final BoostAttribute boostAtt = addAttribute(BoostAttribute.class);

    BoostFastFilter(TokenStream input) {
      super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (input.incrementToken() == false) {
        return false;
      }
      if (termAtt.toString().equals("fast")) {
        boostAtt.setBoost(0.5f);
      }
      return true;
    }
  }

  private static QueryParser parser() {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, true);
            return new TokenStreamComponents(
                tokenizer, new BoostFastFilter(new MockSynonymFilter(tokenizer)));
          }
        };
    return new QueryParser("field", analyzer);
  }

  private static Query fastDogs(int slop) {
    return new MultiPhraseQuery.Builder()
        .add(new Term("field", "fast"))
        .add(new Term[] {new Term("field", "dogs"), new Term("field", "dog")})
        .setSlop(slop)
        .build();
  }

  /** The bug through the parser: the boost on "fast" is lost because "dogs" has a synonym. */
  public void testQuotedPhraseKeepsTokenBoost() throws Exception {
    // control: no synonym in the phrase
    assertEquals(
        new BoostQuery(new PhraseQuery("field", "fast", "cats"), 0.5f),
        parser().parse("\"fast cats\""));
    assertEquals(new BoostQuery(fastDogs(0), 0.5f), parser().parse("\"fast dogs\""));
  }

  /** The explicit slop must survive too, once the phrase is wrapped in a BoostQuery. */
  public void testExplicitSlopKeptWithTokenBoost() throws Exception {
    assertEquals(new BoostQuery(fastDogs(3), 0.5f), parser().parse("\"fast dogs\"~3"));
  }

  /**
   * Related, older problem on the plain-phrase path: the parser applies an explicit slop only to a
   * bare PhraseQuery or MultiPhraseQuery, so a boosted plain phrase loses its "~3".
   */
  public void testExplicitSlopKeptOnBoostedPlainPhrase() throws Exception {
    assertEquals(
        new BoostQuery(new PhraseQuery(3, "field", "fast", "cats"), 0.5f),
        parser().parse("\"fast cats\"~3"));
  }
}
