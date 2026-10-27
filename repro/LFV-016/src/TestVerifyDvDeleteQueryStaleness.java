import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * Independent verification of candidate J: does IndexWriter.deleteDocuments(Query) on a doc-values
 * field see doc-values updates that were issued earlier (smaller sequence number)?
 *
 * <p>All tests use a fixed IndexWriterConfig (NoMergePolicy, no random IWC), ByteBuffersDirectory,
 * single thread, so the outcome does not depend on tests.seed.
 */
public class TestVerifyDvDeleteQueryStaleness extends LuceneTestCase {

  private static Document doc(String id, long v) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("v", v));
    return d;
  }

  private static IndexWriterConfig iwc() {
    IndexWriterConfig c = new IndexWriterConfig();
    c.setMergePolicy(NoMergePolicy.INSTANCE);
    return c;
  }

  private static Query vEquals(long x) {
    return NumericDocValuesField.newSlowExactQuery("v", x);
  }

  private static boolean idLive(DirectoryReader r, String id) throws Exception {
    return new IndexSearcher(r).count(new TermQuery(new Term("id", id))) == 1;
  }

  /** A1: flushed doc, update v 1->5, then delete v==5. Contract: doc deleted. */
  public void testA1_deleteNewValueAfterUpdate_flushedSegment() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc())) {
      w.addDocument(doc("a", 1));
      w.commit();
      long sUpd = w.updateNumericDocValue(new Term("id", "a"), "v", 5);
      long sDel = w.deleteDocuments(vEquals(5));
      assertTrue("seqNo order", sDel > sUpd);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertFalse("A1: doc had v=5 when delete(v==5) was issued, must be gone", idLive(r, "a"));
      }
    }
  }

  /** A2: flushed doc, update v 1->5, then delete v==1. Contract: doc survives. */
  public void testA2_deleteOldValueAfterUpdate_flushedSegment() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc())) {
      w.addDocument(doc("a", 1));
      w.commit();
      w.updateNumericDocValue(new Term("id", "a"), "v", 5);
      w.deleteDocuments(vEquals(1));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertTrue("A2: doc no longer had v=1 when delete(v==1) was issued", idLive(r, "a"));
        // and the update itself was applied
        assertEquals(1, new IndexSearcher(r).count(vEquals(5)));
      }
    }
  }

  /** A3: same as A1, but the doc is still in the RAM buffer (never flushed before). */
  public void testA3_deleteNewValueAfterUpdate_inRamDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc())) {
      w.addDocument(doc("a", 1));
      w.updateNumericDocValue(new Term("id", "a"), "v", 5);
      w.deleteDocuments(vEquals(5));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertFalse("A3: in-RAM doc had v=5 when delete(v==5) was issued", idLive(r, "a"));
      }
    }
  }

  /** C1 (control): open an NRT reader between update and delete -> delete sees v=5. */
  public void testC1_control_nrtOpenBetween() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc())) {
      w.addDocument(doc("a", 1));
      w.commit();
      w.updateNumericDocValue(new Term("id", "a"), "v", 5);
      DirectoryReader.open(w).close(); // resolves AND writes the DV update
      w.deleteDocuments(vEquals(5));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertFalse("C1 control: delete after NRT refresh sees v=5", idLive(r, "a"));
      }
    }
  }

  /** C2 (control): commit between update and delete -> delete sees v=5. */
  public void testC2_control_commitBetween() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc())) {
      w.addDocument(doc("a", 1));
      w.commit();
      w.updateNumericDocValue(new Term("id", "a"), "v", 5);
      w.commit();
      w.deleteDocuments(vEquals(5));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertFalse("C2 control: delete after commit sees v=5", idLive(r, "a"));
      }
    }
  }

  /** C3 (control): reverse order delete(v==5) then update 1->5: doc must survive. */
  public void testC3_control_reverseOrder() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc())) {
      w.addDocument(doc("a", 1));
      w.commit();
      w.deleteDocuments(vEquals(5));
      w.updateNumericDocValue(new Term("id", "a"), "v", 5);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertTrue("C3 control: delete issued while v=1 must not delete", idLive(r, "a"));
      }
    }
  }

  /**
   * B1: cross-packet. The update is frozen into its OWN packet and resolved (by a DWPT flush
   * triggered via maxBufferedDocs=2) before the delete is even issued; the resolved DV update is
   * only pending in RAM (not written). The delete query is then evaluated against the pooled
   * SegmentReader, which does not see pending DV updates.
   */
  public void testB1_crossPacket_resolvedButUnwrittenUpdate() throws Exception {
    IndexWriterConfig c = iwc();
    c.setMaxBufferedDocs(2);
    c.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, c)) {
      w.addDocument(doc("a", 1));
      w.commit();
      w.updateNumericDocValue(new Term("id", "a"), "v", 5);
      // two docs with v=100 trigger a DWPT flush that freezes+applies the global buffer
      w.addDocument(doc("x", 100));
      w.addDocument(doc("y", 100));
      assertEquals("a DWPT flush happened (RAM buffer empty)", 0, w.numRamDocs());
      w.deleteDocuments(vEquals(5));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("update was applied", 0, new IndexSearcher(r).count(vEquals(1)));
        assertFalse("B1: delete(v==5) issued after the update was resolved", idLive(r, "a"));
      }
    }
  }

  /** B2: tryUpdateDocValue (applied in place, pending in RAM), then delete by the new value. */
  public void testB2_tryUpdateDocValueThenDelete() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc())) {
      w.addDocument(doc("a", 1));
      w.commit();
      long sUpd;
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        sUpd = w.tryUpdateDocValue(r0, 0, new NumericDocValuesField("v", 5));
      }
      assertTrue(sUpd > 0);
      long sDel = w.deleteDocuments(vEquals(5));
      assertTrue(sDel > sUpd);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("tryUpdate was applied", 0, new IndexSearcher(r).count(vEquals(1)));
        assertFalse("B2: delete(v==5) after tryUpdateDocValue(v=5)", idLive(r, "a"));
      }
    }
  }

  /**
   * S1: practical soft-deletes pattern - soft delete a doc via updateDocValues on the soft-deletes
   * field, then hard-purge all soft-deleted docs with deleteDocuments(FieldExistsQuery(soft)).
   */
  public void testS1_softDeleteThenPurgeByExistsQuery() throws Exception {
    IndexWriterConfig c = iwc();
    c.setSoftDeletesField("soft");
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, c)) {
      w.addDocument(doc("a", 1));
      w.addDocument(doc("b", 1));
      w.commit();
      w.updateDocValues(new Term("id", "a"), new NumericDocValuesField("soft", 1));
      w.deleteDocuments(new FieldExistsQuery("soft"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        Bits hard = ((SegmentReader) leaf).getHardLiveDocs();
        assertEquals("b is live", 1, r.numDocs());
        assertTrue(
            "S1: soft-deleted doc 'a' must also be HARD deleted by the purge query",
            hard != null && hard.get(0) == false);
      }
    }
  }
}
