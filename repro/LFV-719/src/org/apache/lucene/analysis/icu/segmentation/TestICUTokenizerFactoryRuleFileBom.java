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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

public class TestICUTokenizerFactoryRuleFileBom extends LuceneTestCase {

  public void testRuleFileWithUtf8Bom() throws Exception {
    ICUTokenizerFactory factory =
        new ICUTokenizerFactory(new HashMap<>(Map.of("rulefiles", "Latn:latin.rbbi")));
    factory.inform(new StringMockResourceLoader("\uFEFF.+ {200};\n"));
    List<String> tokens = new ArrayList<>();
    try (Tokenizer tokenizer = factory.create()) {
      CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
      tokenizer.setReader(new StringReader("ab cd"));
      tokenizer.reset();
      while (tokenizer.incrementToken()) {
        tokens.add(term.toString());
      }
      tokenizer.end();
    }
    // same tokens as for the file without the BOM; actual: none (the BOM became rule text)
    assertEquals(List.of("ab cd"), tokens);
  }
}
