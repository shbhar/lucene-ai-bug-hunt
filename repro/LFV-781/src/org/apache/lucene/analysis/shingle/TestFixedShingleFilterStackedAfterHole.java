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
package org.apache.lucene.analysis.shingle;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFixedShingleFilterStackedAfterHole extends LuceneTestCase {

  public void testStackedTokenAfterHole() throws Exception {
    TokenStream ts =
        new FixedShingleFilter(
            new CannedTokenStream(
                new Token("a", 1, 0, 1), new Token("b", 2, 6, 7), new Token("c", 0, 6, 7)),
            2);
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    List<String> shingles = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      shingles.add(term.toString());
    }
    ts.end();
    ts.close();
    // a is at position 0 and b, c are stacked at position 2: no bigram may join a and c
    assertFalse("expected no shingle \"a c\", got " + shingles, shingles.contains("a c"));
  }
}
