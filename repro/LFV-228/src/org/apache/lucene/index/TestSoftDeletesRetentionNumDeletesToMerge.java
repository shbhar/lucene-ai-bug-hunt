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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * SoftDeletesRetentionMergePolicy#numDeletesToMerge treats a document that was soft-deleted and
 * then hard-deleted as "retained" when it matches the retention query. IndexWriter's merge drops
 * such documents (hard deletes win, LUCENE-8381), so the policy under-reports the deletes a merge
 * would claim, and forceMergeDeletes() leaves the hard-deleted document in the index.
 */
public class TestSoftDeletesRetentionNumDeletesToMerge extends LuceneTestCase {

  /**
   * Records what the merge context reports for each segment when forced deletes are computed, and
   * disables natural merges so that only forceMergeDeletes/forceMerge rewrite segments.
   */
  private static final class RecordingPolicy extends FilterMergePolicy {
    final List<Integer> reported = new ArrayList<>();

    RecordingPolicy(MergePolicy in) {
      super(in);
    }

    @Override
    public MergeSpecification findMerges(MergeTrigger t, SegmentInfos infos, MergeContext ctx) {
      return null;
    }

    @Override
    public MergeSpecification findFullFlushMerges(
        MergeTrigger t, SegmentInfos infos, MergeContext ctx) {
      return null;
    }

    @Override
    public MergeSpecification findForcedMerges(
        SegmentInfos infos,
        int maxSegmentCount,
        Map<SegmentCommitInfo, Boolean> segmentsToMerge,
        MergeContext ctx)
        throws IOException {
      if (reported.isEmpty()) { // first call of forceMerge: the segments as they are now
        for (SegmentCommitInfo info : infos) {
          reported.add(ctx.numDeletesToMerge(info));
        }
      }
      return in.findForcedMerges(infos, maxSegmentCount, segmentsToMerge, ctx);
    }
  }

  public void testHardDeletedSoftDeletedDocIsReclaimable() throws Exception {
    try (Directory dir = newDirectory()) {
      RecordingPolicy policy =
          new RecordingPolicy(
              new SoftDeletesRetentionMergePolicy(
                  "soft_delete", MatchAllDocsQuery::new, new TieredMergePolicy()));
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setSoftDeletesField("soft_delete")
              .setMergePolicy(policy)
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 4; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
          w.addDocument(doc);
        }
        w.commit();
        // soft-delete id:0 and id:1 in place
        w.updateDocValues(new Term("id", "0"), new NumericDocValuesField("soft_delete", 1));
        w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("soft_delete", 1));
        w.commit();
        // ... then hard-delete id:0
        w.deleteDocuments(new Term("id", "0"));
        w.commit();
        // a second segment without deletes, so that forceMerge(1) below has to rewrite the first one
        Document extra = new Document();
        extra.add(new StringField("id", "4", Field.Store.YES));
        w.addDocument(extra);
        w.commit();
        assertEquals(2, w.getSegmentCount());
        assertEquals(5, w.getDocStats().maxDoc);

        // forceMerge(1) merges both segments. The merge keeps id:1 (soft-deleted, retained) and
        // drops id:0 (hard-deleted).
        int maxDocBefore = w.getDocStats().maxDoc;
        w.forceMerge(1);
        assertEquals(1, w.getSegmentCount());
        int claimed = maxDocBefore - w.getDocStats().maxDoc;
        assertEquals("numDeletesToMerge seen by the merge policy", 2, policy.reported.size());
        int reported = policy.reported.get(0) + policy.reported.get(1);
        assertEquals("documents the merge actually dropped", 1, claimed);

        // MergePolicy#numDeletesToMerge: "the number of deletes that a merge would claim"
        assertEquals("numDeletesToMerge vs. deletes actually claimed by the merge", claimed, reported);
      }
    }
  }

  public void testForceMergeDeletesLeavesHardDelete() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setSoftDeletesField("soft_delete")
              .setMergePolicy(
                  new RecordingPolicy(
                      new SoftDeletesRetentionMergePolicy(
                          "soft_delete", MatchAllDocsQuery::new, new TieredMergePolicy())))
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 4; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
          w.addDocument(doc);
        }
        w.commit();
        w.updateDocValues(new Term("id", "0"), new NumericDocValuesField("soft_delete", 1));
        w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("soft_delete", 1));
        w.commit();
        w.deleteDocuments(new Term("id", "0")); // 1 of 4 docs hard-deleted = 25% > 10%
        w.commit();

        w.forceMergeDeletes(true);
        // id:1 is retained by the policy (soft-deleted, matches the retention query); id:0 must go
        assertEquals(
            "hard-deleted document still occupies the index after forceMergeDeletes",
            3,
            w.getDocStats().maxDoc);
      }
    }
  }
}
