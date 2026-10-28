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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostAttribute;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryBuilder applies token boosts (BoostAttribute) to phrases: the phrase is boosted by the
 * product of its tokens' boosts. MockSynonymFilter adds the single-word synonym "dog" to "dogs" and
 * the multi-word synonym "cavy" to "guinea pig"; BoostFastFilter gives the token "fast" a boost of
 * 0.5. The boost on "fast" survives a plain phrase and a graph phrase, but not a phrase in which
 * another word has a single-word synonym.
 */
public class TestQueryBuilderMultiPhraseBoosts extends LuceneTestCase {

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

  private static QueryBuilder builder() {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, true);
            return new TokenStreamComponents(
                tokenizer, new BoostFastFilter(new MockSynonymFilter(tokenizer)));
          }
        };
    return new QueryBuilder(analyzer);
  }

  /** Control: no synonym, a plain PhraseQuery, boosted by 0.5. */
  public void testPlainPhraseKeepsTokenBoost() {
    assertEquals(
        new BoostQuery(new PhraseQuery("field", "fast", "cats"), 0.5f),
        builder().createPhraseQuery("field", "fast cats"));
  }

  /** Control: a multi-word synonym gives one phrase per path, each boosted by 0.5. */
  public void testGraphPhraseKeepsTokenBoost() {
    Query expected =
        new BooleanQuery.Builder()
            .add(
                new BoostQuery(new PhraseQuery("field", "fast", "cavy"), 0.5f),
                BooleanClause.Occur.SHOULD)
            .add(
                new BoostQuery(new PhraseQuery("field", "fast", "guinea", "pig"), 0.5f),
                BooleanClause.Occur.SHOULD)
            .build();
    assertEquals(expected, builder().createPhraseQuery("field", "fast guinea pig"));
  }

  /** The bug: "dogs" has the single-word synonym "dog", and the boost on "fast" is lost. */
  public void testMultiPhraseKeepsTokenBoost() {
    Query multiPhrase =
        new MultiPhraseQuery.Builder()
            .add(new Term("field", "fast"))
            .add(new Term[] {new Term("field", "dogs"), new Term("field", "dog")})
            .build();
    assertEquals(
        new BoostQuery(multiPhrase, 0.5f), builder().createPhraseQuery("field", "fast dogs"));
  }
}
