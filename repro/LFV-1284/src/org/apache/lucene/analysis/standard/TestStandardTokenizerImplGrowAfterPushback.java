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
package org.apache.lucene.analysis.standard;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestStandardTokenizerImplGrowAfterPushback extends LuceneTestCase {

  public void testGrowAfterPushbackKeepsSupplementaryChar() throws Exception {
    StandardTokenizerImpl scanner = new StandardTokenizerImpl(new StringReader("ab😀cd"));
    scanner.setBufferSize(3);
    scanner.getNextToken();
    scanner.yypushback(scanner.yylength());
    scanner.setBufferSize(8);
    List<String> tokens = new ArrayList<>();
    while (scanner.getNextToken() != StandardTokenizerImpl.YYEOF) {
      tokens.add(scanner.yytext() + "@" + scanner.yychar());
    }
    // expected: [ab@0, U+1F600@2, cd@4]; actual: [ab@0, cd@3], the emoji is lost and later offsets shift
    assertEquals(List.of("ab@0", "😀@2", "cd@4"), tokens);
  }
}
