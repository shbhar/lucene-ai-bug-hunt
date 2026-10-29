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
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestKnnMergeLeafResultsOrder extends LuceneTestCase {

  public void testMaxScoreBoundsScoresWithDocIdOrderedMerge() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 2; i++) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {i, 0}));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query knn =
            new KnnFloatVectorQuery("v", new float[] {1, 0}, 2) {
              @Override
              protected TopDocs mergeLeafResults(TopDocs[] perLeafResults) {
                TopDocs merged = super.mergeLeafResults(perLeafResults);
                ScoreDoc[] hits = merged.scoreDocs.clone();
                Arrays.sort(hits, Comparator.comparingInt(hit -> hit.doc));
                return new TopDocs(merged.totalHits, hits);
              }
            };
        IndexSearcher searcher = new IndexSearcher(reader);
        Weight weight = searcher.createWeight(searcher.rewrite(knn), ScoreMode.TOP_SCORES, 1f);
        Scorer scorer = weight.scorer(reader.leaves().get(0));
        scorer.advanceShallow(0);
        float maxScore = scorer.getMaxScore(DocIdSetIterator.NO_MORE_DOCS);
        assertEquals(1, scorer.iterator().advance(1));
        float score = scorer.score();
        // doc 1 is the exact match: expected getMaxScore >= its score 1.0, actual 0.5 (doc 0's)
        assertTrue("getMaxScore " + maxScore + " < score " + score, maxScore >= score);
      }
    }
  }
}
