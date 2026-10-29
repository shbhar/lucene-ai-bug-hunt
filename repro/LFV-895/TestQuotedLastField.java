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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.dict.UserDictionary;
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CSVUtil;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (intended-behaviour angle): a quoted value is decoded when it is not the last
 * field, but kept verbatim (quotes included) when it is the last field. Controls show that the
 * same value decodes in a non-final position and that an unquoted final value is untouched.
 */
public class TestQuotedLastField extends LuceneTestCase {

  // Control: quoted value NOT in the last position is decoded.
  public void testControlQuotedNonLastField() {
    assertArrayEquals(new String[] {"p,q", "x"}, CSVUtil.parse("\"p,q\",x"));
  }

  // Control: unquoted last value is untouched.
  public void testControlUnquotedLastField() {
    assertArrayEquals(new String[] {"x", "pq"}, CSVUtil.parse("x,pq"));
  }

  // The value Lucene's own encoder produces is not decoded when it is the last field.
  public void testEncoderOutputAsLastField() {
    String enc = CSVUtil.quoteEscape("p,q");
    assertEquals("\"p,q\"", enc);
    assertArrayEquals(new String[] {"x", "p,q"}, CSVUtil.parse("x," + enc));
  }

  // A part-of-speech that contains a comma can only be written quoted; it then keeps the quotes.
  public void testUserDictionaryPosWithCommaNeedsQuotes() throws Exception {
    UserDictionary dict =
        UserDictionary.open(new StringReader("abcd,a b cd,foo1 foo2 foo3,\"tagA,tagB\"\n"));
    assertEquals(List.of("tagA,tagB", "tagA,tagB", "tagA,tagB"), posTags(dict, Set.of()));
  }

  // Downstream effect: a part-of-speech stop filter configured with the tag does not remove the
  // tokens when the user dictionary quotes the tag (all fields quoted, as QUOTE_ALL writers do).
  public void testStopTagDoesNotMatchQuotedPos() throws Exception {
    UserDictionary dict =
        UserDictionary.open(
            new StringReader("\"abcd\",\"a b cd\",\"foo1 foo2 foo3\",\"bar\"\n"));
    assertEquals(List.of(), posTags(dict, Set.of("bar")));
  }

  // Control: the same dictionary line unquoted gives the plain tag and the stop filter matches it.
  public void testControlUnquotedPos() throws Exception {
    UserDictionary dict = UserDictionary.open(new StringReader("abcd,a b cd,foo1 foo2 foo3,bar\n"));
    assertEquals(List.of("bar", "bar", "bar"), posTags(dict, Set.of()));
    assertEquals(List.of(), posTags(dict, Set.of("bar")));
  }

  private static List<String> posTags(UserDictionary dict, Set<String> stopTags)
      throws Exception {
    JapaneseTokenizer tok = new JapaneseTokenizer(dict, false, JapaneseTokenizer.Mode.SEARCH);
    tok.setReader(new StringReader("abcd"));
    TokenStream ts = new JapanesePartOfSpeechStopFilter(tok, stopTags);
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PartOfSpeechAttribute pos = ts.addAttribute(PartOfSpeechAttribute.class);
    List<String> tags = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      assertNotNull(term.toString());
      tags.add(pos.getPartOfSpeech());
    }
    ts.end();
    ts.close();
    return tags;
  }
}
