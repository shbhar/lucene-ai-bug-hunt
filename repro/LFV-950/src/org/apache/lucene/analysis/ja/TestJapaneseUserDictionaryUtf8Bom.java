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
package org.apache.lucene.analysis.ja;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

/**
 * A kuromoji user dictionary that starts with a UTF-8 byte order mark (BOM, U+FEFF) should load
 * and segment as the file without it, as the line loader of the other file-based factories
 * (WordlistLoader.getLines, "skip initial bom marker") does. UserDictionary.open keeps the BOM
 * glued to the first line: an entry there is rejected as 'Illegal user dictionary entry' (the
 * message shows two strings that look identical), and a comment line there is no longer a comment
 * and fails with ArrayIndexOutOfBoundsException.
 */
public class TestJapaneseUserDictionaryUtf8Bom extends BaseTokenStreamTestCase {

  private static final String BOM = "\uFEFF";
  private static final String ENTRY = "関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,カスタム名詞\n";

  private static Analyzer fromFactory(String fileContent) throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("userDictionary", "userdict.txt");
    JapaneseTokenizerFactory factory = new JapaneseTokenizerFactory(args);
    factory.inform(new StringMockResourceLoader(fileContent));
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(factory.create(newAttributeFactory()));
      }
    };
  }

  /** BOM before the first entry. */
  public void testEntryFirstLineWithBom() throws Exception {
    try (Analyzer a = fromFactory(ENTRY)) {
      assertAnalyzesTo(a, "関西国際空港", new String[] {"関西", "国際", "空港"});
    }
    // main: inform throws RuntimeException "Illegal user dictionary entry \uFEFF関西国際空港 - the
    // concatenated segmentation (関西国際空港) does not match the surface form (\uFEFF関西国際空港)"
    try (Analyzer a = fromFactory(BOM + ENTRY)) {
      assertAnalyzesTo(a, "関西国際空港", new String[] {"関西", "国際", "空港"});
    }
  }

  /** BOM before a first comment line. */
  public void testCommentFirstLineWithBom() throws Exception {
    String dict = "# user dictionary\n" + ENTRY;
    try (Analyzer a = fromFactory(dict)) {
      assertAnalyzesTo(a, "関西国際空港", new String[] {"関西", "国際", "空港"});
    }
    // main: inform throws ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1
    try (Analyzer a = fromFactory(BOM + dict)) {
      assertAnalyzesTo(a, "関西国際空港", new String[] {"関西", "国際", "空港"});
    }
  }

  /**
   * Control: the stop-tags file of JapanesePartOfSpeechStopFilterFactory, read through
   * WordlistLoader.getLines, skips the BOM (passes on main).
   */
  public void testControlStopTagsFileSkipsBom() throws Exception {
    for (String tags : new String[] {"助詞-格助詞-一般\n", BOM + "助詞-格助詞-一般\n"}) {
      Map<String, String> args = new HashMap<>();
      args.put("tags", "stoptags.txt");
      JapanesePartOfSpeechStopFilterFactory factory = new JapanesePartOfSpeechStopFilterFactory(args);
      factory.inform(new StringMockResourceLoader(tags));
      try (Analyzer a =
          new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
              Tokenizer t =
                  new JapaneseTokenizer(
                      newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH);
              return new TokenStreamComponents(t, factory.create(t));
            }
          }) {
        assertAnalyzesTo(a, "空港に行った", new String[] {"空港", "行っ", "た"});
      }
    }
  }
}
