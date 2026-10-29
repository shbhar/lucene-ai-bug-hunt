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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestOsqAntipodalZeroCentroid extends LuceneTestCase {

  public void testAntipodalCosineVectorsCommit() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene104ScalarQuantizedVectorsFormat()));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (float[] v : new float[][] {{1, 0}, {-1, 0}}) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("vec", v, VectorSimilarityFunction.COSINE));
        w.addDocument(doc);
      }
      try {
        w.commit();
      } catch (IllegalArgumentException e) {
        // expected: commit succeeds; actual: flush normalizes the zero centroid and throws
        throw new AssertionError("expected commit of two COSINE vectors to succeed, got " + e, e);
      }
    }
  }
}
