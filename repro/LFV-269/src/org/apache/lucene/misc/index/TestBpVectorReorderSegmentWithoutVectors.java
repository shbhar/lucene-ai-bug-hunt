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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBpVectorReorderSegmentWithoutVectors extends LuceneTestCase {

  public void testMergeSegmentWithoutVectors() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergeScheduler(new SerialMergeScheduler())
            .setMergePolicy(
                new BPReorderingMergePolicy(
                    new LogDocMergePolicy(), new BpVectorReorderer("vector")));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 64; i++) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("vector", new float[] {i, 1}));
        w.addDocument(doc);
      }
      w.commit();
      w.addDocument(new Document());
      w.commit();
      // expected: the two segments merge; actual: NPE from the merged view's FloatVectorValues.copy()
      try {
        w.forceMerge(1);
      } catch (NullPointerException e) {
        throw new AssertionError("expected the two segments to merge, but forceMerge threw " + e, e);
      }
    }
  }
}
