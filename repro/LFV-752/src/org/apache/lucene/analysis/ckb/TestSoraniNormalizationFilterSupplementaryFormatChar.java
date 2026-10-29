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
package org.apache.lucene.analysis.ckb;

import java.io.StringReader;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestSoraniNormalizationFilterSupplementaryFormatChar extends BaseTokenStreamTestCase {

  public void testRemovesFormatCharOutsideBmp() throws Exception {
    Tokenizer tokenizer = new KeywordTokenizer();
    tokenizer.setReader(
        new StringReader("\u06A9\u0648\u0631\u062F\uDB40\uDC01\u0633\u062A\u0627\u0646"));
    // expected: U+E0001, a format char like the removed U+200F, is removed; actual: it is kept
    assertTokenStreamContents(
        new SoraniNormalizationFilter(tokenizer),
        new String[] {"\u06A9\u0648\u0631\u062F\u0633\u062A\u0627\u0646"});
  }
}
