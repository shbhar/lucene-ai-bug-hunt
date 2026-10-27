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
package org.apache.lucene.analysis.compound;

import java.io.StringReader;
import java.util.Arrays;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.xml.sax.InputSource;

/**
 * HyphenationTree.hyphenate skips leading characters that are in no character class and reports
 * the inner hyphenation points as indexes into the given word, but the two synthetic points it adds
 * "at the beginning and end of the word" are 0 and the number of letters, not the start and end of
 * the letters. HyphenationCompoundWordTokenFilter then cuts its candidate subwords at the wrong
 * places and misses dictionary words.
 */
public class TestHyphenationLeadingNonLetter extends BaseTokenStreamTestCase {

  // letters a-z (with upper case), one pattern: a hyphenation point between 'r' and 'w'
  private static final String GRAMMAR =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<hyphenation-info>\n<classes>\n"
          + "aA bB cC dD eE fF gG hH iI jJ kK lL mM nN oO pP qQ rR sS tT uU vV wW xX yY zZ\n"
          + "</classes>\n<patterns>\nr1w\n</patterns>\n</hyphenation-info>\n";

  private static HyphenationTree tree() throws Exception {
    return HyphenationCompoundWordTokenFilter.getHyphenationTree(
        new InputSource(new StringReader(GRAMMAR)));
  }

  private static CharArraySet dict() {
    return new CharArraySet(Arrays.asList("zimmer", "wohnung"), true);
  }

  public void testHyphenationPoints() throws Exception {
    HyphenationTree tree = tree();
    // control: points 0, 6 (zimmer|wohnung) and 13 (end)
    assertArrayEquals(
        new int[] {0, 6, 13}, tree.hyphenate("zimmerwohnung", 1, 1).getHyphenationPoints());
    // a trailing non-letter is ignored and the points are unchanged
    assertArrayEquals(
        new int[] {0, 6, 13}, tree.hyphenate("zimmerwohnung.", 1, 1).getHyphenationPoints());
    // a leading non-letter: the inner point is shifted to 7 (an index into the given word), so the
    // first and last points must be the start (1) and the end (14) of the letters
    assertArrayEquals(
        new int[] {1, 7, 14}, tree.hyphenate("3zimmerwohnung", 1, 1).getHyphenationPoints());
  }

  public void testCompoundFilterStandardTokenizer() throws Exception {
    // StandardTokenizer keeps "3zimmerwohnung" (a number directly followed by letters) as one token
    Tokenizer tok = new StandardTokenizer();
    tok.setReader(new StringReader("zimmerwohnung 3zimmerwohnung"));
    TokenStream ts = new HyphenationCompoundWordTokenFilter(tok, tree(), dict());
    assertTokenStreamContents(
        ts,
        new String[] {
          "zimmerwohnung", "zimmer", "wohnung", "3zimmerwohnung", "zimmer", "wohnung"
        },
        new int[] {1, 0, 0, 1, 0, 0});
  }

  /**
   * A trailing non-letter is skipped and the word is decomposed; a leading one is skipped too
   * (iIgnoreAtBeginning) but the word is then cut at the wrong places.
   */
  public void testCompoundFilterQuotedWord() throws Exception {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader("zimmerwohnung\" \"zimmerwohnung"));
    TokenStream ts = new HyphenationCompoundWordTokenFilter(tok, tree(), dict());
    assertTokenStreamContents(
        ts,
        new String[] {
          "zimmerwohnung\"", "zimmer", "wohnung", "\"zimmerwohnung", "zimmer", "wohnung"
        },
        new int[] {1, 0, 0, 1, 0, 0});
  }
}
