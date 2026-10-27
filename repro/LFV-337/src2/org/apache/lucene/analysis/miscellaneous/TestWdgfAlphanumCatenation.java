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
package org.apache.lucene.analysis.miscellaneous;

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.CATENATE_NUMBERS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.CATENATE_WORDS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.Version;

/**
 * A subword whose first character has type ALPHA|DIGIT (every supplementary character, which
 * WordDelimiterIterator.getType maps to ALPHA|DIGIT, or any character mapped to ALPHANUM in a
 * custom type table) starts a catenation whose type matches both words and numbers. The run is then
 * never flushed at a word/number change: subwords of the other kind are skipped but stay inside the
 * run, so the catenated token joins non-adjacent subwords and/or spans positions of subwords it does
 * not contain.
 *
 * <p>Class javadoc: CATENATE_WORDS catenates "maximum runs of non-numeric subwords ... produced at
 * the same position of the last subword in the run"; CATENATE_NUMBERS "works like CATENATE_WORDS,
 * but for adjacent digit sequences".
 */
/** 9.x variant: same tests minus the query-time test (BooleanQuery API differs before 10.0). */
public class TestWdgfAlphanumCatenation extends BaseTokenStreamTestCase {

  private static final String EMOJI = "\uD83D\uDE00"; // U+1F600 (a supplementary character)

  private static TokenStream wdgf(String text, int flags) {
    Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
    t.setReader(new StringReader(text));
    return new WordDelimiterGraphFilter(t, flags, null);
  }

  /** Control with an ordinary letter (passes on main): "xab" spans the run x, ab only. */
  public void testControlCatenateWords() throws Exception {
    int flags = GENERATE_WORD_PARTS | GENERATE_NUMBER_PARTS | CATENATE_WORDS;
    assertTokenStreamContents(
        wdgf("x-ab-12", flags),
        new String[] {"xab", "x", "ab", "12"},
        null,
        null,
        new int[] {1, 0, 1, 1},
        new int[] {2, 1, 1, 1},
        7);
  }

  /**
   * Same shape with a supplementary character instead of "x". On main "U+1F600 ab" gets position
   * length 3, i.e. it also spans "12", so the graph has a path "U+1F600 ab" that drops "12".
   */
  public void testSupplementaryCatenateWords() throws Exception {
    int flags = GENERATE_WORD_PARTS | GENERATE_NUMBER_PARTS | CATENATE_WORDS;
    assertTokenStreamContents(
        wdgf(EMOJI + "-ab-12", flags),
        new String[] {EMOJI + "ab", EMOJI, "ab", "12"},
        null,
        null,
        new int[] {1, 0, 1, 1},
        new int[] {2, 1, 1, 1},
        8);
  }

  /**
   * CATENATE_NUMBERS: "12" is the only number subword, so there is nothing to catenate. On main the
   * filter emits "U+1F600 12", joining two non-adjacent subwords across "ab".
   */
  public void testSupplementaryCatenateNumbers() throws Exception {
    int flags = GENERATE_WORD_PARTS | GENERATE_NUMBER_PARTS | CATENATE_NUMBERS;
    // control (passes on main)
    assertTokenStreamContents(
        wdgf("x-ab-12", flags), new String[] {"x", "ab", "12"}, null, null, new int[] {1, 1, 1}, new int[] {1, 1, 1}, 7);
    assertTokenStreamContents(
        wdgf(EMOJI + "-ab-12", flags),
        new String[] {EMOJI, "ab", "12"},
        null,
        null,
        new int[] {1, 1, 1},
        new int[] {1, 1, 1},
        8);
  }

  /**
   * Same root cause through the factory's documented "types" option, with '@' mapped to ALPHANUM:
   * "@x-ab-12" with catenateNumbers emits "@x12".
   */
  public void testAlphanumTypeTableCatenateNumbers() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("luceneMatchVersion", Version.LATEST.toString());
    args.put("generateWordParts", "1");
    args.put("generateNumberParts", "1");
    args.put("catenateNumbers", "1");
    args.put("types", "types.txt");
    WordDelimiterGraphFilterFactory factory = new WordDelimiterGraphFilterFactory(args);
    factory.inform(new StringMockResourceLoader("@ => ALPHANUM\n"));
    Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
    t.setReader(new StringReader("@x-ab-12"));
    assertTokenStreamContents(
        factory.create(t),
        new String[] {"@x", "ab", "12"},
        null,
        null,
        new int[] {1, 1, 1},
        new int[] {1, 1, 1},
        8);
  }
}
