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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

@SuppressWarnings("deprecation")
public class TestExplicitSimilarityThresholdTie extends LuceneTestCase {

  public void testFloatExactDuplicates() throws Exception {
    float[] target = {1f, 2f};
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (int i = 0; i < 65; i++) {
          Document doc = new Document();
          float[] v = (i == 0 || i == 64) ? target : new float[] {100f, 100f};
          doc.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query query = new FloatVectorSimilarityQuery.Explicit("v", target, 1.0f);
        List<Integer> docs = new ArrayList<>();
        for (ScoreDoc sd : new IndexSearcher(reader).search(query, 10).scoreDocs) {
          docs.add(sd.doc);
        }
        // docs 0 and 64 are exact copies (similarity 1.0 >= resultSimilarity 1.0); main returns [0]
        assertEquals(List.of(0, 64), docs);
      }
    }
  }
}
