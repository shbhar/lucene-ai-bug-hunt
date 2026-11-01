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

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.OneMergeWrappingMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestHnswMaxConnOneMerge extends LuceneTestCase {

  public void testForceMergeWithDeletionFinishes() throws Exception {
    AtomicInteger abortChecks = new AtomicInteger();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene99HnswVectorsFormat(1, 100, 0)))
            .setMergePolicy(
                new OneMergeWrappingMergePolicy(
                    new TieredMergePolicy(),
                    merge ->
                        new MergePolicy.OneMerge(merge) {
                          @Override
                          public void checkAborted() throws MergePolicy.MergeAbortedException {
                            if (abortChecks.incrementAndGet() > 1_000) {
                              setAborted();
                            }
                            super.checkAborted();
                          }
                        }));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new KnnFloatVectorField("vector", new float[] {i}));
        w.addDocument(doc);
      }
      w.commit();
      w.deleteDocuments(new Term("id", "0"));
      // Expected: forceMerge finishes; actual: rebalanceGraph adds levels forever until aborted.
      w.forceMerge(1);
      assertTrue(
          "forceMerge(1) still running after 1,000 abort checks", abortChecks.get() <= 1_000);
    }
  }
}
