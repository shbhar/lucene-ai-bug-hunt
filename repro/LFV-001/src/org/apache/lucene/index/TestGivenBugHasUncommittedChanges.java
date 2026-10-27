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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Sibling symptom of the same bookkeeping gap: once doc-values-only updates have been resolved
 * into the reader pool by an automatic (RAM-triggered) apply, nothing is pending in DW/BD and
 * changeCount has not moved, so hasUncommittedChanges() reports false although commit() would
 * write new DV files (callers such as Solr's DirectUpdateHandler2.shouldCommit skip the commit).
 */
public class TestGivenBugHasUncommittedChanges extends LuceneTestCase {

  public void testHasUncommittedChangesAfterAutoAppliedDocValuesUpdates() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setRAMBufferSizeMB(0.5)
              .setMergePolicy(NoMergePolicy.INSTANCE)
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          doc.add(new NumericDocValuesField("val", 0));
          w.addDocument(doc);
        }
        w.commit();
        assertFalse(w.hasUncommittedChanges());
        int firstFalseAt = -1;
        for (int i = 0; i < 50_000 && firstFalseAt < 0; i++) {
          w.updateNumericDocValue(new Term("id", Integer.toString(i % 1000)), "val", i + 1);
          if (w.hasUncommittedChanges() == false) {
            firstFalseAt = i;
          }
        }
        long gen = w.getLiveCommitData() == null ? 0 : 1;
        long seq = w.commit();
        System.out.println(
            "hasUncommittedChanges() first false after update #"
                + firstFalseAt
                + "; commit() returned seqNo "
                + seq
                + " (gen marker "
                + gen
                + ")");
        assertEquals(
            "hasUncommittedChanges() must not be false while DV updates are uncommitted",
            -1,
            firstFalseAt);
      }
    }
  }
}
