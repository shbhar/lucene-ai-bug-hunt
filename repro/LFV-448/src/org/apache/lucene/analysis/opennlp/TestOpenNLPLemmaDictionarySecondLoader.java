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
package org.apache.lucene.analysis.opennlp;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

public class TestOpenNLPLemmaDictionarySecondLoader extends BaseTokenStreamTestCase {

  public void testSameDictionaryNameFromSecondLoader() throws Exception {
    try (Analyzer a = lemmaAnalyzer("dogs\tword\tdogA\n");
        Analyzer b = lemmaAnalyzer("dogs\tword\tdogB\n")) {
      // b's own lemmas.txt maps dogs to dogB; actual: dogA from a's same-named lemmas.txt
      assertAnalyzesTo(b, "dogs", new String[] {"dogB"});
    }
  }

  private static Analyzer lemmaAnalyzer(String lemmasTxt) throws IOException {
    return CustomAnalyzer.builder(new StringMockResourceLoader(lemmasTxt))
        .withTokenizer("whitespace")
        .addTokenFilter("openNlpLemmatizer", "dictionary", "lemmas.txt")
        .build();
  }
}
