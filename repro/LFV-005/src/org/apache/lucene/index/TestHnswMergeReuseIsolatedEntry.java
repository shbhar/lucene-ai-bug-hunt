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
package org.apache.lucene.index;

import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestHnswMergeReuseIsolatedEntry extends LuceneTestCase {

  public void testForceMergeAfterDeletingAllNeighboursOfEntryNode() throws Exception {
    Random random = new Random(20261022L);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 1450; i++) {
          float[] v = new float[16];
          for (int d = 0; d < v.length; d++) {
            v[d] = (float) (random.nextGaussian() * 0.3);
          }
          boolean inA = i < 30 || i == 215;
          v[0] += i == 171 ? -10 : inA ? 0 : 10;
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.EUCLIDEAN));
          doc.add(new StringField("cluster", inA ? "A" : "B", Field.Store.NO));
          w.addDocument(doc);
        }
        w.commit();
        w.deleteDocuments(new Term("cluster", "A"));
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        float[] query = new float[16];
        query[0] = 10;
        TopDocs hits = new IndexSearcher(reader).search(new KnnFloatVectorQuery("v", query, 10), 10);
        // expected: 10 hits; actual: 1, the entry doc 171, which lost all neighbours in the merge
        assertEquals("kNN hits among 1419 live docs", 10, hits.scoreDocs.length);
      }
    }
  }
}
