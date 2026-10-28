package org.apache.lucene.index;

import static org.apache.lucene.index.RejectedBlockSupport.ACCEPTED;
import static org.apache.lucene.index.RejectedBlockSupport.assertForceMergeWorks;
import static org.apache.lucene.index.RejectedBlockSupport.config;
import static org.apache.lucene.index.RejectedBlockSupport.describe;
import static org.apache.lucene.index.RejectedBlockSupport.indexScenario;
import static org.apache.lucene.index.RejectedBlockSupport.keepFullyDeleted;
import static org.apache.lucene.index.RejectedBlockSupport.leafCount;
import static org.apache.lucene.index.RejectedBlockSupport.retentionMatchAll;
import static org.apache.lucene.index.RejectedBlockSupport.retentionSeqRange;

import java.io.IOException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent repro (verifier). Parent field configured; accepted documents are single
 * documents; a 2-doc block is then rejected at its child (immense term, a documented non-aborting
 * IllegalArgumentException) and is the only content of its in-memory segment; the merge policy
 * keeps that fully deleted segment. The documented outcome is that later merges keep working, and
 * each test asserts that.
 */
public class TestRejectedBlockMergeFailure extends LuceneTestCase {

  private void run(MergePolicy mp, boolean softDeletes, String label) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(mp, softDeletes, true));
      try {
        indexScenario(w, false, true);
        String segs = describe(w);
        assertEquals("PRECONDITION: writer holds two segments " + segs, 2, leafCount(w));
        assertTrue(
            "PRECONDITION: one segment is fully deleted and lacks the parent field " + segs,
            segs.contains("numDocs=0 parentField=null"));
        assertForceMergeWorks(w, label + " " + segs, ACCEPTED);
      } finally {
        if (w.isOpen()) {
          w.close();
        }
      }
    }
  }

  public void testRetentionMatchAllTiered() throws IOException {
    run(
        retentionMatchAll(new TieredMergePolicy()),
        true,
        "SoftDeletesRetentionMergePolicy(MatchAllDocsQuery, TieredMergePolicy)");
  }

  public void testRetentionMatchAllLogDoc() throws IOException {
    run(
        retentionMatchAll(new LogDocMergePolicy()),
        true,
        "SoftDeletesRetentionMergePolicy(MatchAllDocsQuery, LogDocMergePolicy)");
  }

  public void testRetentionDocValuesRangeTiered() throws IOException {
    run(
        retentionSeqRange(new TieredMergePolicy()),
        true,
        "SoftDeletesRetentionMergePolicy(seq range query, TieredMergePolicy)");
  }

  public void testKeepFullyDeletedSegmentHook() throws IOException {
    run(
        keepFullyDeleted(new TieredMergePolicy()),
        false,
        "FilterMergePolicy(TieredMergePolicy).keepFullyDeletedSegment=true");
  }

  /** The bad segment is committed, so a fresh writer on the same index hits the same failure. */
  public void testNewWriterSessionStillFails() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, config(retentionMatchAll(new TieredMergePolicy()), true, true))) {
        indexScenario(w, false, true);
      }
      String committed = describe(dir);
      IndexWriter w2 =
          new IndexWriter(dir, config(retentionMatchAll(new TieredMergePolicy()), true, true));
      try {
        assertEquals("PRECONDITION: new writer holds two segments " + committed, 2, leafCount(w2));
        assertForceMergeWorks(w2, "new writer session, committed=" + committed, ACCEPTED);
      } finally {
        if (w2.isOpen()) {
          w2.close();
        }
      }
    }
  }
}
