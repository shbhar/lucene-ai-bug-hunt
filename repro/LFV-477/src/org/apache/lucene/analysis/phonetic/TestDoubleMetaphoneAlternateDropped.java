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

import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.codec.language.DoubleMetaphone;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * DoubleMetaphoneFilter ("supporting secondary codes") emits the primary and, when it differs, the
 * alternate (secondary) Double Metaphone code. When the token text already equals its primary
 * code (an upper-case token such as "J" or "SM"), the alternate code is silently dropped, so "J"
 * and "j" (same codes per the encoder) index different code sets.
 */
public class TestDoubleMetaphoneAlternateDropped extends BaseTokenStreamTestCase {

  private static Set<String> codes(String text, boolean inject) throws Exception {
    Set<String> out = new TreeSet<>();
    try (TokenStream ts = new DoubleMetaphoneFilter(whitespaceMockTokenizer(text), 4, inject)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  public void testAlternateKeptForTokenEqualToItsPrimaryCode() throws Exception {
    DoubleMetaphone encoder = new DoubleMetaphone();
    encoder.setMaxCodeLen(4);
    for (String word : new String[] {"J", "SM"}) {
      String primary = encoder.doubleMetaphone(word);
      String alternate = encoder.doubleMetaphone(word, true);
      // the encoder is case-insensitive, and the token equals its own primary code
      assertEquals(word, primary);
      assertEquals(primary, encoder.doubleMetaphone(word.toLowerCase(java.util.Locale.ROOT)));
      assertFalse(alternate.equals(primary));
      for (boolean inject : new boolean[] {false, true}) {
        // the lower-case form gets both codes
        Set<String> lower = codes(word.toLowerCase(java.util.Locale.ROOT), inject);
        assertTrue(lower.toString(), lower.contains(primary) && lower.contains(alternate));
        // the upper-case form must get both codes as well
        Set<String> upper = codes(word, inject);
        assertTrue(
            "inject=" + inject + " " + word + " -> " + upper + ", missing secondary code " + alternate,
            upper.contains(primary) && upper.contains(alternate));
      }
    }
  }
}
