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
package org.apache.lucene.analysis.miscellaneous;

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.IGNORE_KEYWORDS;

import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestWdgfIgnoreKeywordsHole extends BaseTokenStreamTestCase {

  public void testHoleStaysBeforeKeyword() throws Exception {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader("lucene / solr rocks"));
    TokenStream ts =
        new WordDelimiterGraphFilter(
            new SetKeywordMarkerFilter(tokenizer, new CharArraySet(List.of("solr"), false)),
            GENERATE_WORD_PARTS | IGNORE_KEYWORDS,
            null);
    // the hole of the swallowed "/" must stay before keyword "solr": expected {1,2,1}, actual {1,1,2}
    assertTokenStreamContents(ts, new String[] {"lucene", "solr", "rocks"}, new int[] {1, 2, 1});
  }
}
