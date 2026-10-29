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

import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestConcatenateGraphNonAsciiSeparator extends LuceneTestCase {

  public void testEnDashSeparator() throws Exception {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader("new york"));
    try (TokenStream ts = new ConcatenateGraphFilter(tokenizer, '–', false, 100)) {
      TermToBytesRefAttribute term = ts.addAttribute(TermToBytesRefAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      // expected: UTF-8 of "new–york"; actual: AssertionError in the filter (-ea), new 0x13 york (-da)
      assertEquals(new BytesRef("new–york"), term.getBytesRef());
    }
  }
}
