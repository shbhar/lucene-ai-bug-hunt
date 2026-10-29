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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestHnswZeroMergeWorkers extends LuceneTestCase {

  public void testZeroMergeWorkersWithExecutor() throws Exception {
    try (ExecutorService exec = Executors.newSingleThreadExecutor();
        Directory dir = new ByteBuffersDirectory()) {
      KnnVectorsFormat format;
      try {
        format = new Lucene99HnswVectorsFormat(16, 100, 0, exec, 0);
      } catch (IllegalArgumentException e) {
        return;
      }
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.alwaysKnnVectorsFormat(format))
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 2; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", new float[] {i, 1}));
          w.addDocument(doc);
          w.commit();
        }
        // expected: IAE above or a working merge; actual: ArrayIndexOutOfBoundsException
        w.forceMerge(1);
      }
    }
  }
}
