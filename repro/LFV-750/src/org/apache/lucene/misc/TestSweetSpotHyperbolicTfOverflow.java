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
package org.apache.lucene.misc;

import java.io.IOException;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * SweetSpotSimilarity#hyperbolicTf maps a NaN intermediate result to max. The NaN comes from
 * Math.pow overflowing to Infinity, which happens on both sides of xoffset, so far below xoffset
 * the function returns max instead of (approximately) min.
 */
public class TestSweetSpotHyperbolicTfOverflow extends LuceneTestCase {

  /**
   * javadoc: tf(x)=min+(max-min)/2*(((base**(x-xoffset)-base**-(x-xoffset))/(base**(x-xoffset)
   * +base**-(x-xoffset)))+1), i.e. min+(max-min)/2*(tanh((x-xoffset)*ln(base))+1).
   */
  private static double documented(double x, double min, double max, double base, double xoffset) {
    return min + (max - min) / 2 * (Math.tanh((x - xoffset) * Math.log(base)) + 1);
  }

  private static void assertDocumented(float min, float max, double base, float xoffset, float x) {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setHyperbolicTfFactors(min, max, base, xoffset);
    float expected = (float) documented(x, min, max, base, xoffset);
    assertEquals(
        "hyperbolicTf(" + x + ") with min=" + min + " max=" + max + " base=" + base + " xoffset="
            + xoffset,
        expected,
        sim.hyperbolicTf(x),
        1e-6f);
  }

  /** Default base 1.3: base**(xoffset-x) overflows a double once xoffset-x exceeds about 2705. */
  public void testDefaultBaseFarBelowMidpoint() {
    assertDocumented(0f, 2f, 1.3, 3000f, 1f); // documented ~0 (min)
  }

  /** base 10: overflow once xoffset-x exceeds about 308. */
  public void testBase10FarBelowMidpoint() {
    assertDocumented(0f, 2f, 10, 400f, 1f); // documented ~0 (min)
  }

  /** Control: the same parameters just above the overflow point return min as documented. */
  public void testControlJustAboveOverflowPoint() {
    assertDocumented(0f, 2f, 10, 400f, 92f);
  }

  /** The function must be non-decreasing in freq for base > 1 and max > min. */
  public void testMonotoneInFreq() {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setHyperbolicTfFactors(0f, 2f, 10, 400f);
    float prev = 0;
    for (int freq = 1; freq <= 800; freq++) {
      float tf = sim.hyperbolicTf(freq);
      assertTrue(
          "hyperbolicTf(" + freq + ")=" + tf + " < hyperbolicTf(" + (freq - 1) + ")=" + prev,
          freq == 1 || tf >= prev);
      prev = tf;
    }
  }

  private static final class Tokens extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAtt = addAttribute(TermFrequencyAttribute.class);
    private final String[] terms;
    private final int[] freqs;
    private int upto;

    Tokens(String[] terms, int[] freqs) {
      this.terms = terms;
      this.freqs = freqs;
    }

    @Override
    public boolean incrementToken() {
      if (upto == terms.length) {
        return false;
      }
      clearAttributes();
      termAtt.append(terms[upto]);
      freqAtt.setTermFrequency(freqs[upto]);
      upto++;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      upto = 0;
    }
  }

  /**
   * A subclass that uses hyperbolicTf as its tf, as the javadoc suggests ("provided as a
   * convenience for subclasses that want to use a hyperbolic tf function"): with two equally long
   * fields, the one that contains the term once outranks the one that contains it 400 times.
   */
  public void testRankingWithHyperbolicTfSubclass() throws IOException {
    SweetSpotSimilarity sim =
        new SweetSpotSimilarity() {
          @Override
          public float tf(float freq) {
            return hyperbolicTf(freq);
          }
        };
    sim.setHyperbolicTfFactors(0f, 2f, 10, 400f);
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.freeze();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setSimilarity(sim).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document once = new Document(); // doc 0: "a" once, field length 450
        once.add(new Field("body", new Tokens(new String[] {"a", "b"}, new int[] {1, 449}), ft));
        w.addDocument(once);
        Document often = new Document(); // doc 1: "a" 400 times, field length 450
        often.add(new Field("body", new Tokens(new String[] {"a", "b"}, new int[] {400, 50}), ft));
        w.addDocument(often);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        TopDocs td =
            searcher.search(
                new TermQuery(new Term("body", "a")), new TopScoreDocCollectorManager(2, Integer.MAX_VALUE));
        StringBuilder hits = new StringBuilder();
        for (ScoreDoc sd : td.scoreDocs) {
          hits.append(sd.doc).append(':').append(sd.score).append(' ');
        }
        assertEquals("hits: " + hits, 2, td.scoreDocs.length);
        assertEquals("the field with 400 occurrences must rank first; hits: " + hits, 1, td.scoreDocs[0].doc);
      }
    }
  }
}
