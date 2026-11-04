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
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestHnswNaNScoreHidesNeighbours extends LuceneTestCase {

  private static float[] vec(float... head) {
    return Arrays.copyOf(head, 36);
  }

  public void testNaNScoreDoesNotHideOtherNeighbours() throws Exception {
    // Expected: doc 2 (score 4) then doc 0 (score 2); actual: doc 1's NaN score hides doc 2.
    float[][] docs = {vec(0, 0, 1), vec(1e38f, 1e38f, 0), vec(0, 0, 3)};
    float[] query = vec(1e38f, -1e38f, 1);
    try (Directory dir = new MMapDirectory(createTempDir())) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene99HnswVectorsFormat(16, 100, 0)));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (float[] v : docs) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        KnnFloatVectorQuery q = new KnnFloatVectorQuery("v", query, 2);
        ScoreDoc[] hits = new IndexSearcher(reader).search(q, 2).scoreDocs;
        assertEquals(2, hits.length);
        assertEquals(2, hits[0].doc);
        assertEquals(0, hits[1].doc);
      }
    }
  }
}
