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
package org.apache.lucene.analysis.minhash;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * MinHashFilter keeps the hashSetSize lowest values of the token hashes (class javadoc: "keep the
 * 100 lowest values"), so a repeated token must not change its output. With one hash and one bucket
 * the hashes order the three words used here as c &lt; a &lt; b.
 */
public class TestMinHashRepeatedTokens extends BaseTokenStreamTestCase {

  /** The min hashes of {@code text}, 1 hash, 1 bucket, no rotation, in hash order. */
  private static TreeSet<String> minHashes(String text, int hashSetSize) throws IOException {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(text));
    TreeSet<String> out = new TreeSet<>();
    try (TokenStream ts = new MinHashFilter(tokenizer, 1, 1, hashSetSize, false)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** The words whose hashes {@code text} keeps, in hash order, so failures read as words. */
  private static List<String> kept(String text, int hashSetSize) throws IOException {
    Map<String, String> wordOfHash = new HashMap<>();
    for (String word : text.split(" ")) {
      wordOfHash.put(minHashes(word, 1).first(), word);
    }
    List<String> words = new ArrayList<>();
    for (String hash : minHashes(text, hashSetSize)) {
      words.add(wordOfHash.get(hash));
    }
    return words;
  }

  public void testRepeatedTokenKeepsBothLowestValues() throws IOException {
    assertEquals(List.of("a", "b"), kept("b a", 2));
    assertEquals("'b a a' has the same words as 'b a'", kept("b a", 2), kept("b a a", 2));
  }

  public void testSameWordsInAnyOrderGiveTheSameSignature() throws IOException {
    assertEquals(List.of("c", "a"), kept("c a b", 2)); // no repeat: the 2 lowest, c and a
    assertEquals(
        "'c a c b' and 'c a b c'",
        List.of(kept("c a b", 2), kept("c a b", 2)),
        List.of(kept("c a c b", 2), kept("c a b c", 2)));
  }

  public void testDefaultHashSetSizeIsNotAffected() throws IOException {
    assertEquals(kept("b a", 1), kept("b a a", 1));
    assertEquals(kept("c a b", 1), kept("c a c b", 1));
    assertEquals(kept("c a b", 1), kept("c a b c", 1));
  }

  /** The javadoc's "1 hash and keep the 100 lowest values", with 5-word shingles. */
  public void testTextReadTwiceKeepsTheSame100Values() throws IOException {
    StringBuilder once = new StringBuilder();
    StringBuilder twice = new StringBuilder();
    for (int i = 0; i < 154; i++) {
      once.append('w').append(i % 150).append(' ');
    }
    for (int i = 0; i < 304; i++) {
      twice.append('w').append(i % 150).append(' ');
    }
    // both texts have the same 150 distinct shingles
    try (Analyzer analyzer =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter(
                "shingle", "minShingleSize", "5", "maxShingleSize", "5", "outputUnigrams", "false")
            .addTokenFilter("minHash", "hashCount", "1", "bucketCount", "1", "hashSetSize", "100")
            .build()) {
      HashSet<String> sigOnce = terms(analyzer, once.toString());
      HashSet<String> sigTwice = terms(analyzer, twice.toString());
      assertEquals(100, sigOnce.size());
      assertEquals("values kept for the text read twice", 100, sigTwice.size());
      assertTrue("the same 100 values", sigTwice.containsAll(sigOnce));
    }
  }

  /** The root cause: re-adding an element a full set already holds evicts its largest element. */
  public void testFixedSizeTreeSetReAddOfAKeptValue() {
    MinHashFilter.FixedSizeTreeSet<Integer> set = new MinHashFilter.FixedSizeTreeSet<>(2);
    assertTrue(set.add(1));
    assertTrue(set.add(2));
    assertFalse(set.add(1)); // TreeSet.add: "If this set already contains the element ... false"
    assertEquals(List.of(1, 2), new ArrayList<>(set));
  }

  private static HashSet<String> terms(Analyzer analyzer, String text) throws IOException {
    HashSet<String> out = new HashSet<>();
    try (TokenStream ts = analyzer.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }
}
