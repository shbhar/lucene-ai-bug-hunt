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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * SweetSpotSimilarity#lengthNorm computes abs(x-min) + abs(x-max) - (max-min) in int, which
 * overflows for field lengths above 2^30 and turns the norm into NaN.
 */
public class TestSweetSpotLengthNormOverflow extends LuceneTestCase {

  /**
   * lengthNorm javadoc: "1/sqrt( steepness * (abs(x-min) + abs(x-max) - (max-min)) + 1 )" and
   * "This degrades to 1/sqrt(x) when min and max are both 1 and steepness is 0.5" (the defaults).
   */
  public void testDefaultLengthNormAboveTwoToThe30() {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    int numTerms = (1 << 30) + 1; // a legal field length: IndexingChain accepts up to Integer.MAX_VALUE
    float expected = (float) (1.0 / Math.sqrt(numTerms));
    float actual = sim.lengthNorm(numTerms);
    assertEquals(
        "lengthNorm(" + numTerms + ") with default factors", expected, actual, Math.ulp(expected));
  }

  /** One token whose term frequency is set through TermFrequencyAttribute. */
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

  private static final int NUM_DOCS = 3000;
  private static final int LONG_DOC = 2100; // field length 1.1e9, via one token with term freq 1.1e9
  private static final int BEST_SHORT_DOC = 2101; // "a" once, field length 1: score 1.0

  private static Directory buildIndex(Similarity sim, int numDocs, int bestShortDoc)
      throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig().setSimilarity(sim).setCodec(TestUtil.getDefaultCodec());
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS); // custom term frequencies need no positions
    ft.freeze();
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < numDocs; i++) {
        Tokens tokens;
        if (i == LONG_DOC) {
          tokens = new Tokens(new String[] {"a"}, new int[] {1_100_000_000});
        } else if (i == bestShortDoc) {
          tokens = new Tokens(new String[] {"a"}, new int[] {1});
        } else {
          tokens = new Tokens(new String[] {"a", "b"}, new int[] {1, 3}); // score 0.5
        }
        Document doc = new Document();
        doc.add(new Field("body", tokens, ft));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static void check(Similarity sim, boolean exhaustive) throws IOException {
    check(sim, exhaustive, NUM_DOCS, BEST_SHORT_DOC);
  }

  private static void check(Similarity sim, boolean exhaustive, int numDocs, int bestShortDoc)
      throws IOException {
    try (Directory dir = buildIndex(sim, numDocs, bestShortDoc);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      Query query = new TermQuery(new Term("body", "a"));
      int count = searcher.count(query);
      assertEquals(numDocs, count);
      TopDocs complete =
          searcher.search(query, new TopScoreDocCollectorManager(numDocs, Integer.MAX_VALUE));
      if (exhaustive) {
        // Exhaustive collection: every matching document is collected with a finite score.
        float longDocScore = searcher.explain(query, LONG_DOC).getValue().floatValue();
        assertTrue(
            sim + ": score of the long-field document must be finite, got " + longDocScore,
            Float.isFinite(longDocScore));
        assertEquals(
            sim + ": exhaustive search must collect every match",
            count,
            complete.scoreDocs.length);
      } else {
        // Default top-k search (TOP_SCORES) must return the same top hits as the exhaustive one.
        TopDocs top = searcher.search(query, 3);
        assertEquals(
            sim + ": exhaustive best hit " + complete.scoreDocs[0].doc + ":" + complete.scoreDocs[0].score
                + " vs default top-k best hit " + top.scoreDocs[0].doc + ":" + top.scoreDocs[0].score,
            complete.scoreDocs[0].doc,
            top.scoreDocs[0].doc);
      }
    }
  }

  /** Control: ClassicSimilarity, whose lengthNorm SweetSpotSimilarity degrades to by default. */
  public void testClassicSimilarityControl() throws IOException {
    check(new ClassicSimilarity(), true);
    check(new ClassicSimilarity(), false);
  }

  /** Default SweetSpotSimilarity: the long-field document scores NaN and is never collected. */
  public void testSweetSpotLongFieldDocumentIsDropped() throws IOException {
    check(new SweetSpotSimilarity(), true);
  }

  /**
   * Default SweetSpotSimilarity: the NaN block maximum makes top-k search skip the block, so the
   * best short document (score 1.0, next to the long one) is missing from the top hits too.
   */
  public void testSweetSpotLongFieldDocumentHidesBestHit() throws IOException {
    check(new SweetSpotSimilarity(), false);
  }

  /** Same, with the best short document 5,900 documents after the long one (9,000 documents). */
  public void testSweetSpotLongFieldDocumentHidesDistantBestHit() throws IOException {
    check(new SweetSpotSimilarity(), false, 9000, 8000);
  }

  /** Control for the distant case. */
  public void testClassicSimilarityDistantControl() throws IOException {
    check(new ClassicSimilarity(), false, 9000, 8000);
  }
}
