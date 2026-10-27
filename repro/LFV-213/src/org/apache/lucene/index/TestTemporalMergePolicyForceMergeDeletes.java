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
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * TemporalMergePolicy#findForcedDeletesMerges (and #findForcedMerges) skip every time window that
 * holds fewer than two candidate segments, so a segment that is alone in its window is never
 * rewritten: IndexWriter#forceMergeDeletes() leaves its deleted documents in place no matter how
 * many there are.
 */
public class TestTemporalMergePolicyForceMergeDeletes extends LuceneTestCase {

  private static IndexWriter newWriter(Directory dir) throws Exception {
    TemporalMergePolicy policy = new TemporalMergePolicy().setTemporalField("ts"); // defaults
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(policy)
            .setMergeScheduler(new SerialMergeScheduler());
    return new IndexWriter(dir, iwc);
  }

  private static void addDocs(IndexWriter w, long tsSeconds, String prefix, int count)
      throws Exception {
    for (int i = 0; i < count; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", prefix + i, Field.Store.NO));
      doc.add(new LongPoint("ts", tsSeconds));
      w.addDocument(doc);
    }
  }

  /** One segment, half of it deleted: forceMergeDeletes() must expunge the deletes. */
  public void testForceMergeDeletesSingleSegment() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir)) {
      long now = System.currentTimeMillis() / 1000;
      addDocs(w, now - 60, "a", 10);
      w.commit();
      for (int i = 0; i < 5; i++) {
        w.deleteDocuments(new Term("id", "a" + i)); // 50% deleted, threshold is 10%
      }
      w.commit();
      assertEquals(1, w.getSegmentCount());
      assertEquals(10, w.getDocStats().maxDoc);

      MergePolicy.MergeObserver observer = w.forceMergeDeletes(true);
      assertEquals(
          "50% deleted > forceMergeDeletesPctAllowed=10%, yet no merge was scheduled ("
              + observer
              + ")",
          5,
          w.getDocStats().maxDoc);
    }
  }

  /**
   * Two segments in two different time windows, each with 50% deletes: none of them is rewritten.
   */
  public void testForceMergeDeletesOneSegmentPerWindow() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir)) {
      long now = System.currentTimeMillis() / 1000;
      addDocs(w, now - 60, "a", 10); // recent window
      w.commit();
      addDocs(w, now - 30L * 24 * 3600, "b", 10); // a window about 30 days back
      w.commit();
      for (int i = 0; i < 5; i++) {
        w.deleteDocuments(new Term("id", "a" + i));
        w.deleteDocuments(new Term("id", "b" + i));
      }
      w.commit();
      assertEquals(2, w.getSegmentCount());
      w.forceMergeDeletes(true);
      assertFalse(
          "deleted documents left after forceMergeDeletes: " + w.cloneSegmentInfos(),
          w.hasDeletions());
    }
  }
}
