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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestKnnCosineZeroQueryTarget extends LuceneTestCase {

  /**
   * KnnFloatVectorField rejects a zero vector on a COSINE field. A zero query target on the same
   * field should be rejected too, or score the documents with valid COSINE scores (in [0, 1]).
   */
  public void testZeroQueryTargetOnCosineField() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (float[] v : new float[][] {{1, 0}, {0, 1}}) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.COSINE));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        TopDocs td;
        try {
          td = new IndexSearcher(r).search(new KnnFloatVectorQuery("v", new float[] {0, 0}, 1), 1);
        } catch (IllegalArgumentException expected) {
          return; // rejecting the zero target, as the field does, is fine
        }
        assertEquals(1, td.scoreDocs.length);
        float score = td.scoreDocs[0].score;
        assertTrue("COSINE score must be in [0, 1], was " + score, score >= 0 && score <= 1);
      }
    }
  }
}
