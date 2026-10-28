/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import org.apache.lucene.analysis.ja.dict.UserDictionary;
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.ja.tokenattributes.ReadingAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A user dictionary written with every field quoted (as CSV writers commonly do) is read through
 * CSVUtil.parse: the surface, segmentation and readings are unquoted, but the part-of-speech, the
 * last field of each line, keeps its quotes, so the token reports the part-of-speech with literal
 * quote characters.
 */
public class TestUserDictionaryQuotedFields extends LuceneTestCase {

  public void testQuotedPartOfSpeechIsUnquoted() throws Exception {
    String quoted = "\"abcd\",\"a b cd\",\"foo1 foo2 foo3\",\"bar\"\n";
    UserDictionary dict = UserDictionary.open(new StringReader(quoted));
    JapaneseTokenizer tok = new JapaneseTokenizer(dict, false, JapaneseTokenizer.Mode.SEARCH);
    tok.setReader(new StringReader("abcd"));
    CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
    ReadingAttribute reading = tok.addAttribute(ReadingAttribute.class);
    PartOfSpeechAttribute pos = tok.addAttribute(PartOfSpeechAttribute.class);
    List<String> terms = new ArrayList<>(), readings = new ArrayList<>(), tags = new ArrayList<>();
    tok.reset();
    while (tok.incrementToken()) {
      terms.add(term.toString());
      readings.add(reading.getReading());
      tags.add(pos.getPartOfSpeech());
    }
    tok.end();
    tok.close();
    assertEquals(List.of("a", "b", "cd"), terms); // passes: segmentation field was unquoted
    assertEquals(List.of("foo1", "foo2", "foo3"), readings); // passes: readings field was unquoted
    assertEquals(List.of("bar", "bar", "bar"), tags); // actual: ["\"bar\"", "\"bar\"", "\"bar\""]
  }
}
