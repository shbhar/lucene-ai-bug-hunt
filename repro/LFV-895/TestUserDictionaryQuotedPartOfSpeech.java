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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.dict.UserDictionary;
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.ja.tokenattributes.ReadingAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/** A user dictionary entry with every field quoted should behave like the unquoted entry. */
public class TestUserDictionaryQuotedPartOfSpeech extends LuceneTestCase {

  // the "Silly entry" of the kuromoji test userdict.txt, as is and with every field quoted
  private static final String PLAIN = "abcd,a b cd,foo1 foo2 foo3,bar\n";
  private static final String QUOTED = "\"abcd\",\"a b cd\",\"foo1 foo2 foo3\",\"bar\"\n";

  public void testUnquotedEntry() throws IOException { // passes today
    assertEquals(List.of("a/foo1/bar", "b/foo2/bar", "cd/foo3/bar"), tokens(PLAIN, Set.of()));
    assertEquals(List.of(), tokens(PLAIN, Set.of("bar")));
  }

  public void testQuotedEntry() throws IOException {
    assertEquals(List.of("a/foo1/bar", "b/foo2/bar", "cd/foo3/bar"), tokens(QUOTED, Set.of()));
  }

  public void testStopTagRemovesQuotedEntry() throws IOException {
    assertEquals(List.of(), tokens(QUOTED, Set.of("bar")));
  }

  /** Tokenizes "abcd"; returns term/reading/part-of-speech of every token the stop filter keeps. */
  private static List<String> tokens(String userDict, Set<String> stopTags) throws IOException {
    UserDictionary dict = UserDictionary.open(new StringReader(userDict));
    JapaneseTokenizer tokenizer = new JapaneseTokenizer(dict, false, JapaneseTokenizer.Mode.SEARCH);
    tokenizer.setReader(new StringReader("abcd"));
    List<String> out = new ArrayList<>();
    try (TokenStream ts = new JapanesePartOfSpeechStopFilter(tokenizer, stopTags)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ReadingAttribute reading = ts.addAttribute(ReadingAttribute.class);
      PartOfSpeechAttribute pos = ts.addAttribute(PartOfSpeechAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term + "/" + reading.getReading() + "/" + pos.getPartOfSpeech());
      }
      ts.end();
    }
    return out;
  }
}
