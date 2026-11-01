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
package org.apache.lucene.codecs.lucene99;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestHnswBeamWidthOneMerge extends LuceneTestCase {

  // Expected: forceMerge builds the HNSW graph with beamWidth = 1.
  // Actual: IllegalArgumentException "initialCapacity must be > 0 ... got: 0"; the writer closes.
  public void testForceMergeWithBeamWidthOne() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene99HnswVectorsFormat(16, 1)))
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 1000; i++) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {i, i % 7}));
        w.addDocument(doc);
        if (i == 499) {
          w.flush();
        }
      }
      w.forceMerge(1);
    }
  }
}
