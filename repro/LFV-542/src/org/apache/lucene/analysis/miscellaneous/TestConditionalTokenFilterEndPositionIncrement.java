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

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestConditionalTokenFilterEndPositionIncrement extends BaseTokenStreamTestCase {

  public void testTrailingHoleKeptThroughIdentityDelegate() throws IOException {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader("abc the"));
    TokenStream stop = new StopFilter(tokenizer, new CharArraySet(List.of("the"), false));
    TokenStream ts =
        new ConditionalTokenFilter(stop, in -> in) {
          @Override
          protected boolean shouldFilter() {
            return true;
          }
        };
    // expected final position increment 1 (the removed "the", as StopFilter alone reports); was 0
    assertTokenStreamContents(
        ts, new String[] {"abc"}, null, null, null, null, null, null, 1, null, true, null);
  }
}
