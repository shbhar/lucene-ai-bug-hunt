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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * SynonymScorer sums the per-term frequencies in float (each int freq is first converted to float),
 * while the merged impacts that bound its score carry the exact integer sum, which MaxScoreCache
 * then converts to float once. Above 2^24 the float sum can round up past the converted integer sum,
 * so a document scores above getMaxScore and a top-k search with dynamic pruning skips it.
 */
public class TestSynonymMaxScoreFreqSumRounding extends LuceneTestCase {

  /** Emits {@code countA} times "a", then {@code countB} times "b", then {@code countZ} times "z". */
  private static final class RepeatTokenStream extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final int countA, countB, countZ;
    private int i;

    RepeatTokenStream(int countA, int countB, int countZ) {
      this.countA = countA;
      this.countB = countB;
      this.countZ = countZ;
    }

    @Override
    public boolean incrementToken() {
      clearAttributes();
      if (i < countA) {
        term.append('a');
      } else if (i < countA + countB) {
        term.append('b');
      } else if (i < countA + countB + countZ) {
        term.append('z');
      } else {
        return false;
      }
      i++;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      i = 0;
    }
  }

  private static final FieldType TYPE = new FieldType();

  static {
    TYPE.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    TYPE.setTokenized(true);
    TYPE.freeze();
  }

  private static Document doc(int a, int b, int z) {
    Document d = new Document();
    d.add(new Field("f", new RepeatTokenStream(a, b, z), TYPE));
    return d;
  }

  // 2^24 + 3 converts to the float 2^24 + 4; adding 3 gives 2^24 + 7, which rounds to 2^24 + 8.
  // The exact sum 2^24 + 6 is a float, so the bound uses freq 2^24 + 6.
  private static final int BIG_A = (1 << 24) + 3;
  private static final int SMALL_B = 3;
  private static final int FREQ_SUM = BIG_A + SMALL_B; // 2^24 + 6

  private static final Similarity SIM = new ClassicSimilarity();

  private static Query synonymQuery() {
    return new SynonymQuery.Builder("f")
        .addTerm(new Term("f", "a"))
        .addTerm(new Term("f", "b"))
        .build();
  }

  /**
   * doc 0: "a" x (2^24+6) (same length as doc 300, so the same norm); docs 1..899 except 300:
   * "a b" plus 8 filler tokens; doc 300: "a" x (2^24+3) and "b" x 3. Doc 0 and doc 300 fall in
   * different 256-doc postings blocks of "a".
   */
  private static Directory buildIndex(boolean withDoc0) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setSimilarity(SIM);
    iwc.setRAMBufferSizeMB(256);
    iwc.setMergePolicy(newLogMergePolicy());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 900; i++) {
        if (i == 0) {
          w.addDocument(withDoc0 ? doc(FREQ_SUM, 0, 0) : doc(1, 1, 8));
        } else if (i == 300) {
          w.addDocument(doc(BIG_A, SMALL_B, 0));
        } else {
          w.addDocument(doc(1, 1, 8));
        }
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static IndexSearcher searcher(DirectoryReader reader) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(SIM);
    searcher.setQueryCache(null);
    return searcher;
  }

  /** Scorer#getMaxScore(upTo) must bound the score of every document up to upTo. */
  public void testScoreAboveMaxScore() throws Exception {
    try (Directory dir = buildIndex(false);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = searcher(reader);
      Weight weight =
          searcher.createWeight(searcher.rewrite(synonymQuery()), ScoreMode.TOP_SCORES, 1f);
      LeafReaderContext ctx = reader.leaves().get(0);
      Scorer scorer = weight.scorer(ctx);
      int target = 300;
      int upTo = scorer.advanceShallow(target);
      float maxScore = scorer.getMaxScore(upTo);
      assertEquals(target, scorer.iterator().advance(target));
      float score = scorer.score();
      assertTrue(
          "doc " + target + " scores " + score + " but getMaxScore(" + upTo + ") = " + maxScore,
          score <= maxScore);
    }
  }

  /** A top-1 search must find the same best hit with and without dynamic pruning. */
  public void testTopHitWithPruning() throws Exception {
    try (Directory dir = buildIndex(true);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = searcher(reader);
      Query query = synonymQuery();
      TopDocs exact = searcher.search(query, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
      TopDocs pruned = searcher.search(query, new TopScoreDocCollectorManager(1, 1));
      // On main the exhaustive search ranks doc 300 first (its float freq rounds up to 2^24 + 8).
      String msg =
          "pruned search returned doc "
              + pruned.scoreDocs[0].doc
              + " (score "
              + pruned.scoreDocs[0].score
              + ") but the exhaustive search returned doc "
              + exact.scoreDocs[0].doc
              + " (score "
              + exact.scoreDocs[0].score
              + ")";
      assertEquals(msg, exact.scoreDocs[0].doc, pruned.scoreDocs[0].doc);
      assertEquals(msg, exact.scoreDocs[0].score, pruned.scoreDocs[0].score, 0f);
    }
  }
}
