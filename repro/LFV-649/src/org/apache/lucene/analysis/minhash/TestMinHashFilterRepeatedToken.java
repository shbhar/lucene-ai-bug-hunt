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
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * MinHashFilter javadoc: "The number of hashes used and the number of minimum values for each hash
 * can be set. You could have 1 hash and keep the 100 lowest values ...". With one hash and one
 * bucket, the output must therefore be the hashSetSize lowest distinct hash values of the input
 * tokens: a function of the SET of input tokens. When an input token repeats while the kept set is
 * full and the token is below the current maximum, the maximum is dropped and nothing replaces it.
 * Uses only public API.
 */
public class TestMinHashFilterRepeatedToken extends BaseTokenStreamTestCase {

  /** one hash, one bucket, keep the {@code hashSetSize} lowest values, no rotation */
  private static List<String> minHashes(String text, int hashSetSize) throws IOException {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(text));
    TokenStream ts = new MinHashFilter(tokenizer, 1, 1, hashSetSize, false);
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    List<String> out = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString());
    }
    ts.end();
    ts.close();
    return out;
  }

  /**
   * Oracle built from the filter itself: the min hash of a single word is that word's hash value,
   * and with one hash the token text orders exactly like the hash value (unsigned high-to-low
   * 16-bit chars). So the expected output is the hashSetSize smallest single-word outputs.
   */
  private static List<String> expectedLowest(String text, int hashSetSize) throws IOException {
    TreeSet<String> all = new TreeSet<>();
    for (String word : text.split(" ")) {
      List<String> one = minHashes(word, hashSetSize);
      assertEquals(1, one.size());
      all.add(one.get(0));
    }
    List<String> lowest = new ArrayList<>();
    for (String h : all) {
      if (lowest.size() == hashSetSize) break;
      lowest.add(h);
    }
    return lowest;
  }

  /** hash values as hex, so assertion messages stay readable */
  private static List<String> hex(List<String> terms) {
    List<String> out = new ArrayList<>();
    for (String t : terms) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < t.length(); i++) {
        sb.append(String.format(java.util.Locale.ROOT, "%04x", (int) t.charAt(i)));
      }
      out.add(sb.toString());
    }
    return out;
  }

  public void testRepeatedTokenKeepsLowestValues() throws IOException {
    // control: no repeated token
    assertEquals(hex(expectedLowest("b a", 2)), hex(minHashes("b a", 2)));
    // the same two distinct tokens, "a" repeated: the two lowest values are still those of a and b
    assertEquals(hex(expectedLowest("b a a", 2)), hex(minHashes("b a a", 2)));
  }

  public void testSketchDependsOnlyOnTokenSet() throws IOException {
    // 150 distinct words, then 20 of them again: the token set is unchanged, so "keep the 100
    // lowest values" must give the same 100 values
    StringBuilder distinct = new StringBuilder();
    for (int i = 0; i < 150; i++) {
      distinct.append(i == 0 ? "" : " ").append("w").append(i);
    }
    StringBuilder repeated = new StringBuilder(distinct);
    for (int i = 0; i < 20; i++) {
      repeated.append(" w").append(i * 7);
    }
    List<String> expected = minHashes(distinct.toString(), 100);
    assertEquals(100, expected.size());
    List<String> actual = minHashes(repeated.toString(), 100);
    List<String> missing = new ArrayList<>(expected);
    missing.removeAll(actual);
    List<String> extra = new ArrayList<>(actual);
    extra.removeAll(expected);
    assertTrue(
        "output has "
            + actual.size()
            + " values; missing of the 100 lowest: "
            + hex(missing)
            + "; output values not among the 100 lowest: "
            + hex(extra),
        missing.isEmpty() && extra.isEmpty() && actual.size() == 100);
  }

  public void testIndexedSketchOfSameTokenSet() throws IOException {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new WhitespaceTokenizer();
            return new TokenStreamComponents(tokenizer, new MinHashFilter(tokenizer, 1, 1, 2, false));
          }
        };
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setStoreTermVectors(true);
    ft.freeze();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document d1 = new Document();
      d1.add(new Field("sketch", "b a", ft));
      w.addDocument(d1);
      Document d2 = new Document();
      d2.add(new Field("sketch", "b a a", ft));
      w.addDocument(d2);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Terms t1 = r.termVectors().get(0).terms("sketch");
        Terms t2 = r.termVectors().get(1).terms("sketch");
        // both documents have the token set {a, b}, so both must index the same 2 min hashes
        assertEquals("doc 'b a' sketch size", 2, t1.size());
        assertEquals("doc 'b a a' sketch size", 2, t2.size());
      }
    }
    analyzer.close();
  }
}
