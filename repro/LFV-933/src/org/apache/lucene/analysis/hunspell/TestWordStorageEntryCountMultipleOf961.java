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
package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ResourceLoader;

/**
 * A plain word list (lowercase, no flags, no affixes) loads with 960 and 962 entries but not with 961 = 31 * 31.
 * About 6% of the words are 5-letter "-ing" forms; the other words end in 70 different syllables, evenly used.
 * Public API only (Dictionary with the default hashFactor, Hunspell).
 */
public class TestWordStorageEntryCountMultipleOf961 extends LuceneTestCase {
  private static final String C = "bdfgklmnprstvz", V = "aeiou";

  /** One consonant-vowel syllable, k in [0, 70). */
  private static String syllable(int k) {
    return "" + C.charAt(k % C.length()) + V.charAt(k / C.length());
  }

  /**
   * Distinct lowercase pseudo-words: 4-letter words of two consonant-vowel syllables (the last syllable varies
   * fastest, so the 70 possible endings are evenly used), and every 16th word a 5-letter "-ing" form (syllable +
   * "ing"), i.e. about 6% of the list.
   */
  static List<String> words(int count) {
    List<String> out = new ArrayList<>();
    int plain = 0, ing = 0;
    while (out.size() < count) {
      if (out.size() % 16 == 15) {
        out.add(syllable(ing++) + "ing");
      } else {
        out.add(syllable(plain / 70 % 70) + syllable(plain % 70));
        plain++;
      }
    }
    return out;
  }

  private static Hunspell load(List<String> words) throws Exception {
    StringBuilder dic = new StringBuilder().append(words.size()).append('\n');
    for (String w : words) dic.append(w).append('\n');
    Dictionary d =
        new Dictionary(
            new ByteBuffersDirectory(),
            "wordcount",
            new ByteArrayInputStream("SET UTF-8\n".getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.toString().getBytes(StandardCharsets.UTF_8)));
    return new Hunspell(d);
  }

  private static void assertLoadsAndSpells(int count) throws Exception {
    List<String> words = words(count);
    Hunspell h = load(words);
    for (String w : words) assertTrue(count + " words: " + w, h.spell(w));
  }

  /** Control: one word fewer. Passes on main. */
  public void testControl960Words() throws Exception {
    assertLoadsAndSpells(960);
  }

  /** Control: one word more. Passes on main. */
  public void testControl962Words() throws Exception {
    assertLoadsAndSpells(962);
  }

  /** 961 = 31 * 31 entries: on main, loading throws RuntimeException "Too many collisions". */
  public void test961Words() throws Exception {
    assertLoadsAndSpells(961);
  }

  /**
   * The configuration path: HunspellStemFilterFactory with a base dictionary plus a custom word list, as in its
   * javadoc example (dictionary="en_GB.dic,my_custom.dic"). The factory has no hashFactor parameter.
   */
  private static void informFactory(int baseWords, int customWords) throws Exception {
    List<String> all = words(baseWords + customWords);
    Map<String, String> files = new HashMap<>();
    files.put("lang.aff", "SET UTF-8\n");
    StringBuilder base = new StringBuilder().append(baseWords).append('\n');
    for (String w : all.subList(0, baseWords)) base.append(w).append('\n');
    StringBuilder custom = new StringBuilder().append(customWords).append('\n');
    for (String w : all.subList(baseWords, all.size())) custom.append(w).append('\n');
    files.put("base.dic", base.toString());
    files.put("custom.dic", custom.toString());
    ResourceLoader loader =
        new ResourceLoader() {
          @Override
          public InputStream openResource(String resource) {
            return new ByteArrayInputStream(files.get(resource).getBytes(StandardCharsets.UTF_8));
          }

          @Override
          public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
            throw new UnsupportedOperationException(cname);
          }
        };
    Map<String, String> args = new HashMap<>();
    args.put("dictionary", "base.dic,custom.dic");
    args.put("affix", "lang.aff");
    new HunspellStemFilterFactory(args).inform(loader);
  }

  /** Control: base 900 words + custom 60 words (960 entries). Passes on main. */
  public void testControlFactoryBasePlusCustom960() throws Exception {
    informFactory(900, 60);
  }

  /** Base 900 words + custom 61 words (961 entries): on main, inform() throws "Too many collisions". */
  public void testFactoryBasePlusCustom961() throws Exception {
    informFactory(900, 61);
  }

  /** Control: base 900 words + custom 62 words (962 entries). Passes on main. */
  public void testControlFactoryBasePlusCustom962() throws Exception {
    informFactory(900, 62);
  }

  /**
   * The exception message says "Try a larger Dictionary#hashFactor"; doubling it keeps the table size a multiple of
   * 961 (1922), and on main the same 961 words still fail to load. (Non-integer factors such as 1.5 do load.)
   */
  public void test961WordsWithDoubledHashFactor() throws Exception {
    List<String> words = words(961);
    StringBuilder dic = new StringBuilder().append(words.size()).append('\n');
    for (String w : words) dic.append(w).append('\n');
    Dictionary d =
        new Dictionary(
            new ByteBuffersDirectory(),
            "wordcount",
            new ByteArrayInputStream("SET UTF-8\n".getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.toString().getBytes(StandardCharsets.UTF_8))) {
          @Override
          protected double hashFactor() {
            return 2.0;
          }
        };
    Hunspell h = new Hunspell(d);
    for (String w : words) assertTrue("hashFactor 2.0, 961 words: " + w, h.spell(w));
  }
}
