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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLogMergePolicyForceMergeDeletesWhileMerging extends LuceneTestCase {

  private volatile boolean runMerges;

  public void testForceMergeDeletesWhileNaturalMergeIsPending() throws Exception {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(2);
    MergeScheduler heldUntilRunMerges =
        new SerialMergeScheduler() {
          @Override
          public synchronized void merge(MergeSource mergeSource, MergeTrigger trigger)
              throws IOException {
            if (runMerges) {
              super.merge(mergeSource, trigger);
            }
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig().setMergePolicy(mp).setMergeScheduler(heldUntilRunMerges);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      addSegment(w, "a", 10);
      addSegment(w, "b", 2);
      w.deleteDocuments(new Term("id", "a0"), new Term("id", "b0"));
      addSegment(w, "c", 1);
      runMerges = true;
      // expected: a0 is merged away; actual: segment a is rejected with the already-merging b
      w.forceMergeDeletes(true);
      IndexWriter.DocStats stats = w.getDocStats();
      assertEquals("deleted docs after forceMergeDeletes(true)", 0, stats.maxDoc - stats.numDocs);
    }
  }

  private static void addSegment(IndexWriter w, String prefix, int numDocs) throws IOException {
    for (int i = 0; i < numDocs; i++) {
      w.addDocument(List.of(new StringField("id", prefix + i, Field.Store.NO)));
    }
    w.flush();
  }
}
