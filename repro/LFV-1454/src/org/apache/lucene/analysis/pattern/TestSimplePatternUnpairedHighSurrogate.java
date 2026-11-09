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
package org.apache.lucene.analysis.pattern;

import java.io.StringReader;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestSimplePatternUnpairedHighSurrogate extends BaseTokenStreamTestCase {

  public void testUnpairedHighSurrogateKeepsNextChar() throws Exception {
    // expected tokens "hi\uD83D" [0,3) and "yo" [4,6); actual: one token "hi\uD83D yo" [0,6)
    for (Tokenizer t :
        new Tokenizer[] {new SimplePatternTokenizer("[^ ]+"), new SimplePatternSplitTokenizer(" ")}) {
      t.setReader(new StringReader("hi\uD83D yo"));
      assertTokenStreamContents(
          t, new String[] {"hi\uD83D", "yo"}, new int[] {0, 4}, new int[] {3, 6}, 6);
    }
  }
}
