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

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

/**
 * A user dictionary saved as UTF-8 with a byte order mark (the bytes EF BB BF, decoded as U+FEFF)
 * should load and segment like the same file without the mark.
 */
public class TestJapaneseUserDictionaryUtf8Bom extends BaseTokenStreamTestCase {

  private static final String BOM = "\uFEFF";

  /** The dictionary of the upstream testUserDict: its first line is a comment. */
  private static final String USER_DICT =
      "# Custom segmentation for long entries\n"
          + "日本経済新聞,日本 経済 新聞,ニホン ケイザイ シンブン,カスタム名詞\n"
          + "関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,テスト名詞\n"
          + "# Custom reading for sumo wrestler\n"
          + "朝青龍,朝青龍,アサショウリュウ,カスタム人名\n";

  /** One entry on line 1. */
  private static final String ENTRY_FIRST = "関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,テスト名詞\n";

  private static Tokenizer fromFactory(String fileContent) throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("userDictionary", "userdict.txt");
    JapaneseTokenizerFactory factory = new JapaneseTokenizerFactory(args);
    factory.inform(new StringMockResourceLoader(fileContent));
    return factory.create(newAttributeFactory());
  }

  /** BOM before a comment line 1 (the shape of the upstream test dictionary). */
  public void testUserDictWithBom() throws IOException {
    Tokenizer ts = fromFactory(USER_DICT);
    ts.setReader(new StringReader("関西国際空港に行った"));
    assertTokenStreamContents(ts, new String[] {"関西", "国際", "空港", "に", "行っ", "た"});

    // Same file with a BOM. Fails on main in inform():
    // ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1
    ts = fromFactory(BOM + USER_DICT);
    ts.setReader(new StringReader("関西国際空港に行った"));
    assertTokenStreamContents(ts, new String[] {"関西", "国際", "空港", "に", "行っ", "た"});
  }

  /** BOM before an entry on line 1. */
  public void testEntryFirstLineWithBom() throws IOException {
    Tokenizer ts = fromFactory(ENTRY_FIRST);
    ts.setReader(new StringReader("関西国際空港"));
    assertTokenStreamContents(ts, new String[] {"関西", "国際", "空港"});

    // Fails on main in inform(): RuntimeException: Illegal user dictionary entry
    // <U+FEFF>関西国際空港 - the concatenated segmentation (関西国際空港) does not match
    // the surface form (<U+FEFF>関西国際空港)
    ts = fromFactory(BOM + ENTRY_FIRST);
    ts.setReader(new StringReader("関西国際空港"));
    assertTokenStreamContents(ts, new String[] {"関西", "国際", "空港"});
  }

  /**
   * Control: the stop-tags file of JapanesePartOfSpeechStopFilterFactory is read through
   * WordlistLoader.getLines, which skips the BOM (passes on main).
   */
  public void testStopTagsFileWithBom() throws IOException {
    for (String tags : new String[] {"助詞-格助詞-一般\n", BOM + "助詞-格助詞-一般\n"}) {
      Map<String, String> args = new HashMap<>();
      args.put("tags", "stoptags.txt");
      JapanesePartOfSpeechStopFilterFactory factory =
          new JapanesePartOfSpeechStopFilterFactory(args);
      factory.inform(new StringMockResourceLoader(tags));
      Tokenizer tokenizer =
          new JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH);
      tokenizer.setReader(new StringReader("空港に行った"));
      TokenStream ts = factory.create(tokenizer);
      assertTokenStreamContents(ts, new String[] {"空港", "行っ", "た"});
    }
  }
}
