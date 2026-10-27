package org.apache.lucene.index;

import static org.apache.lucene.index.RejectedBlockSupport.ACCEPTED;
import static org.apache.lucene.index.RejectedBlockSupport.assertForceMergeWorks;
import static org.apache.lucene.index.RejectedBlockSupport.config;
import static org.apache.lucene.index.RejectedBlockSupport.describe;
import static org.apache.lucene.index.RejectedBlockSupport.indexScenario;
import static org.apache.lucene.index.RejectedBlockSupport.leafCount;
import static org.apache.lucene.index.RejectedBlockSupport.retentionMatchAll;

import java.io.IOException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Controls (expected to pass): each removes one precondition of the failing scenario. */
public class TestRejectedBlockMergeControls extends LuceneTestCase {

  /** Default merge policy: the fully deleted segment is dropped, nothing to merge it with. */
  public void testDefaultMergePolicyDropsSegment() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config(new TieredMergePolicy(), false, true))) {
      indexScenario(w, false, true);
      assertEquals("fully deleted segment dropped: " + describe(w), 1, leafCount(w));
      assertForceMergeWorks(w, "default MP", ACCEPTED);
    }
  }

  /** No rejection: the second block is accepted. */
  public void testNoRejection() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, config(retentionMatchAll(new TieredMergePolicy()), true, true))) {
      indexScenario(w, false, false);
      assertForceMergeWorks(w, "no rejection " + describe(w), ACCEPTED + 2);
    }
  }

  /** No parent field configured. */
  public void testNoParentField() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, config(retentionMatchAll(new TieredMergePolicy()), true, false))) {
      indexScenario(w, false, true);
      assertEquals("two segments " + describe(w), 2, leafCount(w));
      assertForceMergeWorks(w, "no parent field " + describe(w), ACCEPTED);
    }
  }

  /**
   * Scope probe: the accepted documents are 2-doc blocks, so segment A has hasBlocks=true. On main,
   * IndexWriter.mergeMiddle then skips the merged view (hasBlocksButNoParentField) and so never
   * calls FieldInfos.getMergedFieldInfos.
   */
  public void testAcceptedBlocksSegmentMergesFine() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, config(retentionMatchAll(new TieredMergePolicy()), true, true))) {
      indexScenario(w, true, true);
      String segs = describe(w);
      assertEquals("two segments " + segs, 2, leafCount(w));
      assertTrue(segs, segs.contains("numDocs=0 parentField=null"));
      assertForceMergeWorks(w, "accepted docs as blocks " + segs, ACCEPTED);
    }
  }
}
