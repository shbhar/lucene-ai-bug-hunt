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
package org.apache.lucene.analysis.icu.segmentation;

import java.io.StringReader;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestICUTokenizerKatakanaWithoutCjkAsWords extends BaseTokenStreamTestCase {

  public void testKatakanaRunIsOneToken() throws Exception {
    Tokenizer tokenizer = new ICUTokenizer(new DefaultICUTokenizerConfig(false, true));
    tokenizer.setReader(new StringReader("デジタルカメラ"));
    // expected one token (cjkAsWords=false: UAX#29 defaults, WB13); actual デジタル, カメラ
    assertTokenStreamContents(tokenizer, new String[] {"デジタルカメラ"});
  }
}
