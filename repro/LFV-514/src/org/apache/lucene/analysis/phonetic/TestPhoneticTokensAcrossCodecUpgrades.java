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
package org.apache.lucene.analysis.phonetic;

import org.apache.commons.codec.Encoder;
import org.apache.commons.codec.language.ColognePhonetic;
import org.apache.commons.codec.language.Metaphone;
import org.apache.commons.codec.language.RefinedSoundex;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Pins the phonetic tokens of the previous release. Each assertion is the output of the release
 * named in the method comment; on main (and, for RefinedSoundex, since 10.2.0) the bundled
 * commons-codec version produces different tokens, and neither CHANGES.txt nor MIGRATE.md says
 * that existing phonetic fields need re-indexing. A failure here shows such a silent change.
 */
public class TestPhoneticTokensAcrossCodecUpgrades extends BaseTokenStreamTestCase {

  private static TokenStream phonetic(String text, Encoder encoder, boolean inject) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new java.io.StringReader(text));
    return new PhoneticFilter(t, encoder, inject);
  }

  /** Lucene 10.5.2 (commons-codec 1.17.2): Metaphone encodes "chantaient" as KNTN. */
  public void testMetaphoneSameAsLastRelease() throws Exception {
    assertTokenStreamContents(phonetic("chantaient", new Metaphone(), false), new String[] {"KNTN"});
  }

  /** Lucene 10.5.2 (commons-codec 1.17.2): ColognePhonetic encodes "quick" as 44. */
  public void testColognePhoneticSameAsLastRelease() throws Exception {
    assertTokenStreamContents(
        phonetic("quick", new ColognePhonetic(), false), new String[] {"44"});
  }

  /** Lucene 10.5.2 (commons-codec 1.17.2): Daitch-Mokotoff encodes "it's" as 034000. */
  public void testDaitchMokotoffSameAsLastRelease() throws Exception {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new java.io.StringReader("it's"));
    assertTokenStreamContents(new DaitchMokotoffSoundexFilter(t, false), new String[] {"034000"});
  }

  /**
   * Lucene 10.1.0 (commons-codec 1.13): RefinedSoundex emits no code for "Köln", only the original
   * token; since 10.2.0 (commons-codec 1.17.2) it also emits K378.
   */
  public void testRefinedSoundexSameAs10_1() throws Exception {
    assertTokenStreamContents(
        phonetic("K\u00f6ln", new RefinedSoundex(), true), new String[] {"K\u00f6ln"});
  }
}
