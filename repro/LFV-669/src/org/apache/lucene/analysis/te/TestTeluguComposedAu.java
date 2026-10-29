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
package org.apache.lucene.analysis.te;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestTeluguComposedAu extends BaseTokenStreamTestCase {

  public void testComposedAuFoldsToOo() throws Exception {
    Tokenizer tokenizer = whitespaceMockTokenizer("ఒౌషధం");
    // expected: ఒ + ౌ (AU) folds to OO like precomposed ఔ (ఔషధం -> ఓషధం); actual: AU is kept (ఔషధం)
    assertTokenStreamContents(new TeluguNormalizationFilter(tokenizer), new String[] {"ఓషధం"});
  }
}
