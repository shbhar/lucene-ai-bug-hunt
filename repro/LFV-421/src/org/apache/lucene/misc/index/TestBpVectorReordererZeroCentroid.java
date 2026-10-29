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
package org.apache.lucene.misc.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBpVectorReordererZeroCentroid extends LuceneTestCase {

  public void testHalfThatSumsToZero() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 64; i++) {
          float[] v = i < 32 ? new float[] {i % 2 == 0 ? 1 : -1, 0} : new float[] {0, 1};
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.DOT_PRODUCT));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        CodecReader leaf = (CodecReader) r.leaves().get(0).reader();
        // expected a DocMap; actual: docs 0..31 sum to (0, 0), the centroid is scaled by 1/sqrt(0) to NaN
        assertNotNull(new BpVectorReorderer("v").computeDocMap(leaf, dir, null));
      }
    }
  }
}
