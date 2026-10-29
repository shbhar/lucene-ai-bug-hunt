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
package org.apache.lucene.analysis.ko;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

/**
 * A nori user dictionary that starts with a UTF-8 byte order mark (BOM, U+FEFF) should give the
 * same segmentation as the file without it, as the line loader of the other file-based factories
 * (WordlistLoader.getLines, "skip initial bom marker") does. UserDictionary.open keeps the BOM
 * glued to the first entry, so that entry silently never matches.
 */
public class TestKoreanUserDictionaryUtf8Bom extends BaseTokenStreamTestCase {

  private static final String BOM = "\uFEFF";
  private static final String DICT = "세종시 세종 시\n";

  private static Analyzer fromFactory(String fileContent) throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("userDictionary", "userdict.txt");
    KoreanTokenizerFactory factory = new KoreanTokenizerFactory(args);
    factory.inform(new StringMockResourceLoader(fileContent));
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(factory.create(newAttributeFactory()));
      }
    };
  }

  private static Analyzer fromUserDictionary(UserDictionary dict) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t =
            new KoreanTokenizer(
                newAttributeFactory(), dict, KoreanTokenizer.DecompoundMode.DISCARD, false);
        return new TokenStreamComponents(t);
      }
    };
  }

  /** KoreanTokenizerFactory userDictionary file. */
  public void testFactoryUserDictionaryFileWithBom() throws Exception {
    try (Analyzer a = fromFactory(DICT)) {
      assertAnalyzesTo(a, "세종시", new String[] {"세종", "시"});
    }
    try (Analyzer a = fromFactory(BOM + DICT)) {
      // main: [세, 종시] (the user entry is lost)
      assertAnalyzesTo(a, "세종시", new String[] {"세종", "시"});
    }
  }

  /** UserDictionary.open(Reader) directly. */
  public void testUserDictionaryOpenWithBom() throws Exception {
    try (Analyzer a = fromUserDictionary(UserDictionary.open(new StringReader(DICT)))) {
      assertAnalyzesTo(a, "세종시", new String[] {"세종", "시"});
    }
    try (Analyzer a = fromUserDictionary(UserDictionary.open(new StringReader(BOM + DICT)))) {
      // main: [세, 종시]
      assertAnalyzesTo(a, "세종시", new String[] {"세종", "시"});
    }
  }
}
