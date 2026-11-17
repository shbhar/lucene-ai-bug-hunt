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
package org.apache.lucene.queryparser.flexible.standard;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestOpenRangeBoundWithNormalizingAnalyzer extends LuceneTestCase {

  private static final class CollatedFilter extends TokenFilter {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    CollatedFilter(TokenStream input) {
      super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (input.incrementToken() == false) {
        return false;
      }
      String term = termAtt.toString();
      termAtt.setEmpty().append("collated").append(term);
      return true;
    }
  }

  private static final class CollatedAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer tokenizer = new MockTokenizer();
      return new TokenStreamComponents(tokenizer, new CollatedFilter(tokenizer));
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
      return new CollatedFilter(in);
    }
  }

  public void testOpenUpperBound() throws Exception {
    try (Analyzer analyzer = new CollatedAnalyzer()) {
      // expected: field:[collatedabc TO *]; actual: field:[collatedabc TO collated]
      assertEquals(
          TermRangeQuery.newStringRange("field", "collatedabc", null, true, true),
          new StandardQueryParser(analyzer).parse("[abc TO *]", "field"));
    }
  }
}
