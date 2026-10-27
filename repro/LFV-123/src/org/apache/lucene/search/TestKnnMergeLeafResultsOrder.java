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

import java.util.Arrays;
import java.util.Comparator;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * AbstractKnnVectorQuery#mergeLeafResults is a documented extension point whose result has "no
 * constraint on their ordering". The rest of rewrite() and DocAndScoreQuery nevertheless assume
 * descending score order: the max score is taken from the first entry, and the minimum top-k
 * score used by the re-entry pass from the last one. A subclass that only reorders the merged hits
 * (here: by doc id) therefore changes which documents a search returns.
 */
public class TestKnnMergeLeafResultsOrder extends LuceneTestCase {

  /** Returns the same merged hits as the default implementation, in doc id order. */
  static final class DocOrderedKnnQuery extends KnnFloatVectorQuery {
    DocOrderedKnnQuery(String field, float[] target, int k) {
      super(field, target, k);
    }

    @Override
    protected TopDocs mergeLeafResults(TopDocs[] perLeafResults) {
      TopDocs merged = super.mergeLeafResults(perLeafResults);
      ScoreDoc[] hits = merged.scoreDocs.clone();
      Arrays.sort(hits, Comparator.comparingInt(sd -> sd.doc));
      return new TopDocs(merged.totalHits, hits);
    }
  }

  private static IndexWriterConfig iwc() {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static int[] sortedDocs(TopDocs td) {
    return Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).sorted().toArray();
  }

  /**
   * DocAndScoreQuery takes maxScore from the first merged entry. With doc order that is a low score,
   * so top-score pruning in a disjunction skips the best kNN hit.
   */
  public void testMaxScoreTakenFromFirstEntry() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc())) {
        for (int i = 0; i < 200_000; i++) {
          Document d = new Document();
          d.add(new StringField("f", "t", Field.Store.NO));
          if (i < 4) { // four far-away vectors at the start of the index
            d.add(new KnnFloatVectorField("v", new float[] {10 + i, 1}, VectorSimilarityFunction.EUCLIDEAN));
          }
          w.addDocument(d);
        }
        Document d = new Document(); // doc 200000: the query vector itself, no term
        d.add(new KnnFloatVectorField("v", new float[] {0, 1}, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(d);
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        float[] target = {0, 1};
        for (Query knn :
            new Query[] {new KnnFloatVectorQuery("v", target, 5), new DocOrderedKnnQuery("v", target, 5)}) {
          Query q =
              new BooleanQuery.Builder()
                  .add(knn, BooleanClause.Occur.SHOULD)
                  .add(
                      new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("f", "t"))), 0.5f),
                      BooleanClause.Occur.SHOULD)
                  .build();
          // doc 200000 scores 1.0 (exact match); every other doc scores at most 0.5 + 0.01
          TopDocs top = searcher.search(q, new TopScoreDocCollectorManager(1, 1));
          assertEquals(knn.getClass().getSimpleName(), 200_000, top.scoreDocs[0].doc);
        }
      }
    }
  }

  /**
   * rewrite() takes the minimum top-k score from the last merged entry to decide which segments to
   * re-enter. With doc order it skips the re-entry and returns a different, worse set of hits.
   */
  public void testReentryMinScoreTakenFromLastEntry() throws Exception {
    int segments = 10, perSegment = 2000, k = 50;
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc())) {
        for (int s = 0; s < segments; s++) {
          for (int i = 0; i < perSegment; i++) {
            // the last segment holds the query's neighbours, nearest at the end of the segment
            float x = s == segments - 1 ? perSegment - 1 - i : (segments - s) * perSegment + i;
            Document d = new Document();
            d.add(new KnnFloatVectorField("v", new float[] {x, 1}, VectorSimilarityFunction.EUCLIDEAN));
            w.addDocument(d);
          }
          w.commit();
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        float[] target = {0, 1};
        TopDocs expected = searcher.search(new KnnFloatVectorQuery("v", target, k), k);
        TopDocs actual = searcher.search(new DocOrderedKnnQuery("v", target, k), k);
        // only the order of the merged hits differs, so the same documents must match
        assertArrayEquals(sortedDocs(expected), sortedDocs(actual));
      }
    }
  }
}
