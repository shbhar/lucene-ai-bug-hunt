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
package org.apache.lucene.codecs.lucene104;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestScalarQuantizedEuclideanOverflow extends LuceneTestCase {

  public void testLargeComponentGivesFiniteScore() throws Exception {
    float[] spike = new float[16];
    spike[0] = -1e19f;
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene104ScalarQuantizedVectorsFormat()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (float[] vector : new float[][] {spike, new float[16]}) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", vector, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        VectorScorer scorer = getOnlyLeafReader(reader).getFloatVectorValues("v").scorer(spike);
        assertEquals(0, scorer.iterator().nextDoc());
        float score = scorer.score();
        TopDocs hits = new IndexSearcher(reader).search(new KnnFloatVectorQuery("v", spike, 1), 1);
        // expected: doc 0 found with a finite score (1.0 with the float format); actual: NaN and 0 hits
        assertTrue(
            "expected doc 0 found with a finite score, but its score is " + score + " and the query found " + hits.scoreDocs.length + " hits",
            Float.isFinite(score) && hits.scoreDocs.length == 1 && hits.scoreDocs[0].doc == 0);
      }
    }
  }
}
