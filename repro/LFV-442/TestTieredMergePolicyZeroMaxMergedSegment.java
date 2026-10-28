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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TieredMergePolicy#setMaxMergedSegmentMB accepts 0 ("must be >=0"), but with a 0-byte maximum
 * doFindMerges builds an empty merge candidate and fails (AssertionError with -ea,
 * IndexOutOfBoundsException with -da).
 */
public class TestTieredMergePolicyZeroMaxMergedSegment extends LuceneTestCase {

  /** Returns null if the setter rejects 0 (a valid fix), else a writer using it. */
  private static IndexWriter newWriter(Directory dir) throws Exception {
    TieredMergePolicy tmp = new TieredMergePolicy();
    try {
      tmp.setMaxMergedSegmentMB(0.0); // accepted on main: the setter only rejects v < 0
    } catch (IllegalArgumentException rejected) {
      return null;
    }
    assertEquals(0.0, tmp.getMaxMergedSegmentMB(), 0.0);
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setMergePolicy(tmp)
            .setMergeScheduler(new SerialMergeScheduler())
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(16);
    return new IndexWriter(dir, iwc);
  }

  private static void addDocs(IndexWriter w, int from, int to) throws Exception {
    for (int i = from; i < to; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      w.addDocument(doc);
    }
  }

  /** forceMergeDeletes on two segments that each have one deleted document. */
  public void testForceMergeDeletes() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir)) {
      if (w == null) return;
      addDocs(w, 0, 5);
      w.flush();
      addDocs(w, 5, 10);
      w.flush();
      w.deleteDocuments(new Term("id", "0"), new Term("id", "5"));
      w.commit();
      try {
        w.forceMergeDeletes(); // 20% deletes per segment > forceMergeDeletesPctAllowed (10%)
      } catch (Throwable t) {
        throw new AssertionError("forceMergeDeletes failed with maxMergedSegmentMB=0", t);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(8, r.numDocs());
      }
    }
  }

  /** Plain indexing: a segment with more deletes than deletesPctAllowed reaches the same code. */
  public void testNaturalMergeAfterDeletes() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir)) {
      if (w == null) return;
      addDocs(w, 0, 10);
      w.flush();
      // 50% of the only segment deleted: above deletesPctAllowed (20%), so findMerges does not
      // set it aside as "too large" and hands it to doFindMerges
      for (int i = 0; i < 5; i++) {
        w.deleteDocuments(new Term("id", Integer.toString(i)));
      }
      try {
        addDocs(w, 10, 11);
        w.commit();
      } catch (Throwable t) {
        throw new AssertionError("commit failed with maxMergedSegmentMB=0", t);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(6, r.numDocs());
      }
    }
  }
}
