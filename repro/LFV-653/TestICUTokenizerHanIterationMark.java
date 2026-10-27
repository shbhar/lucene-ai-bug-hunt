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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * With cjkAsWords=false, ICUTokenizer must keep U+3005 IDEOGRAPHIC ITERATION MARK, a letter
 * (Word_Break=ALetter) of the Han script, as in 人々 ("people") and 時々 ("sometimes").
 */
public class TestICUTokenizerHanIterationMark extends BaseTokenStreamTestCase {
  /** cjkAsWords=false: "segmented according to UAX#29 defaults" */
  private Analyzer uax29;

  /** cjkAsWords=false followed by CJKBigramFilter, as in TestWithCJKBigramFilter */
  private Analyzer bigrams;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    uax29 =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(
                new ICUTokenizer(
                    newAttributeFactory(), new DefaultICUTokenizerConfig(false, true)));
          }
        };
    bigrams =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source =
                new ICUTokenizer(newAttributeFactory(), new DefaultICUTokenizerConfig(false, true));
            TokenStream result = new CJKBigramFilter(source);
            return new TokenStreamComponents(source, result);
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(uax29, bigrams);
    super.tearDown();
  }

  /** Controls, pass on main: the default config and StandardTokenizer keep 々. */
  public void testControls() throws Exception {
    Tokenizer defaultConfig = new ICUTokenizer();
    defaultConfig.setReader(new StringReader("人々"));
    assertTokenStreamContents(defaultConfig, new String[] {"人々"});

    Tokenizer standard = new StandardTokenizer();
    standard.setReader(new StringReader("人々"));
    assertTokenStreamContents(standard, new String[] {"人", "々"});

    // a second ideograph in the same place is kept
    assertAnalyzesTo(uax29, "人人", new String[] {"人", "人"});
  }

  public void testIterationMarkAfterKanji() throws Exception {
    assertAnalyzesTo(uax29, "人々", new String[] {"人", "々"}, new int[] {0, 1}, new int[] {1, 2});
    assertAnalyzesTo(uax29, "時々", new String[] {"時", "々"});
  }

  public void testIterationMarkInSentence() throws Exception {
    assertAnalyzesTo(uax29, "様々な人々", new String[] {"様", "々", "な", "人", "々"});
    assertAnalyzesTo(uax29, "佐々木さん", new String[] {"佐", "々", "木", "さ", "ん"});
  }

  /** The other Han-script Word_Break=ALetter characters: U+303B and U+16FE3. */
  public void testOtherHanLetters() throws Exception {
    assertAnalyzesTo(uax29, "人〻", new String[] {"人", "〻"});
    assertAnalyzesTo(uax29, "人𖿣", new String[] {"人", "𖿣"});
  }

  /** 々 typed IDEOGRAPHIC (as cjkAsWords=true does) lets CJKBigramFilter form the word. */
  public void testBigramFilter() throws Exception {
    assertAnalyzesTo(bigrams, "人々", new String[] {"人々"}, new String[] {"<DOUBLE>"});
  }
}
