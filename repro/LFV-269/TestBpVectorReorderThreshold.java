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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Probe of the size threshold: forceMerge(1) of a segment with N vectors and a 10-document
 * segment without the field, BPReorderingMergePolicy + BpVectorReorderer with default settings
 * (minPartitionSize 32). By the code, partitioning, and with it copy(), starts at N >= 64.
 */
public class TestBpVectorReorderThreshold extends LuceneTestCase {

  private static void run(int vectors) throws IOException {
    BPReorderingMergePolicy mp =
        new BPReorderingMergePolicy(new LogDocMergePolicy(), new BpVectorReorderer("vector"));
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(mp)
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < vectors; i++) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("vector", new float[] {i % 7, 1 + i % 3}));
        w.addDocument(doc);
      }
      w.commit();
      for (int i = 0; i < 10; i++) {
        w.addDocument(new Document());
      }
      w.commit();
      w.forceMerge(1);
      assertTrue(w.isOpen());
    }
  }

  public void test63Vectors() throws IOException {
    run(63);
  }

  public void test64Vectors() throws IOException {
    run(64);
  }
}
