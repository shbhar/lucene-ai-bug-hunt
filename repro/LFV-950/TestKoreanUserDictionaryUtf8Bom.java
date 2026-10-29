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

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

/**
 * A user dictionary saved as UTF-8 with a byte order mark (the bytes EF BB BF, decoded as U+FEFF)
 * should give the same segmentation as the same file without the mark.
 */
public class TestKoreanUserDictionaryUtf8Bom extends BaseTokenStreamTestCase {

  private static final String BOM = "\uFEFF";

  /** The compound entry of the upstream testUserDict, here on line 1 of the file. */
  private static final String USER_DICT = "세종시 세종 시\n";

  private static Tokenizer fromFactory(String fileContent) throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("userDictionary", "userdict.txt");
    KoreanTokenizerFactory factory = new KoreanTokenizerFactory(args);
    factory.inform(new StringMockResourceLoader(fileContent));
    return factory.create(newAttributeFactory());
  }

  private static Tokenizer fromUserDictionary(UserDictionary dict) {
    return new KoreanTokenizer(
        newAttributeFactory(), dict, KoreanTokenizer.DecompoundMode.DISCARD, false);
  }

  /** Control: without a user dictionary, 세종시 is segmented as [세, 종시]. */
  public void testNoUserDictionary() throws IOException {
    Tokenizer ts = fromUserDictionary(null);
    ts.setReader(new StringReader("세종시"));
    assertTokenStreamContents(ts, new String[] {"세", "종시"});
  }

  /** KoreanTokenizerFactory with the userDictionary argument. */
  public void testFactoryUserDictionaryWithBom() throws IOException {
    Tokenizer ts = fromFactory(USER_DICT);
    ts.setReader(new StringReader("세종시"));
    assertTokenStreamContents(ts, new String[] {"세종", "시"});

    // Same file with a BOM. Fails on main: [세, 종시], the entry is silently lost.
    ts = fromFactory(BOM + USER_DICT);
    ts.setReader(new StringReader("세종시"));
    assertTokenStreamContents(ts, new String[] {"세종", "시"});
  }

  /** UserDictionary.open(Reader) directly. */
  public void testUserDictionaryOpenWithBom() throws IOException {
    Tokenizer ts = fromUserDictionary(UserDictionary.open(new StringReader(USER_DICT)));
    ts.setReader(new StringReader("세종시"));
    assertTokenStreamContents(ts, new String[] {"세종", "시"});

    // Fails on main: [세, 종시]
    ts = fromUserDictionary(UserDictionary.open(new StringReader(BOM + USER_DICT)));
    ts.setReader(new StringReader("세종시"));
    assertTokenStreamContents(ts, new String[] {"세종", "시"});
  }
}
