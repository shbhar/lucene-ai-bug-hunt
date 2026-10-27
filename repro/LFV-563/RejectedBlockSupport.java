package org.apache.lucene.index;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

/**
 * Shared helpers for the independent repro: only public IndexWriter / IndexWriterConfig /
 * MergePolicy / DirectoryReader API is used.
 */
final class RejectedBlockSupport {
  static final String PARENT = "_parent";
  static final String SOFT = "_soft_deletes";

  /**
   * Accepted documents in segment A. With 10 accepted documents and 1 rejected document the delete
   * ratio is 1/11 (9%), below TieredMergePolicy's default deletesPctAllowed (20%), so the commit
   * that flushes the fully deleted segment does not trigger a delete-reclaiming singleton merge of
   * that segment (which would remove it before forceMerge sees it).
   */
  static final int ACCEPTED = 10;

  private RejectedBlockSupport() {}

  static MergePolicy retentionMatchAll(MergePolicy in) {
    return new SoftDeletesRetentionMergePolicy(SOFT, MatchAllDocsQuery::new, in);
  }

  static MergePolicy retentionSeqRange(MergePolicy in) {
    return new SoftDeletesRetentionMergePolicy(
        SOFT, () -> NumericDocValuesField.newSlowRangeQuery("seq", 0, Long.MAX_VALUE), in);
  }

  static MergePolicy keepFullyDeleted(MergePolicy in) {
    return new FilterMergePolicy(in) {
      @Override
      public boolean keepFullyDeletedSegment(IOSupplier<CodecReader> readerIOSupplier) {
        return true;
      }
    };
  }

  static Document doc(String id, long seq) {
    Document d = new Document();
    d.add(new NumericDocValuesField("seq", seq));
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** A child whose second field carries a term one byte longer than MAX_TERM_LENGTH. */
  static Document badChild(long seq) {
    Document d = new Document();
    d.add(new NumericDocValuesField("seq", seq));
    char[] chars = new char[IndexWriter.MAX_TERM_LENGTH + 1];
    Arrays.fill(chars, 'x');
    d.add(new StringField("id", new String(chars), Field.Store.NO));
    return d;
  }

  static IndexWriterConfig config(MergePolicy mp, boolean softDeletes, boolean parentField) {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    if (parentField) {
      iwc.setParentField(PARENT);
    }
    if (softDeletes) {
      iwc.setSoftDeletesField(SOFT);
    }
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    iwc.setRAMBufferSizeMB(64);
    return iwc;
  }

  /**
   * Segment A: ACCEPTED accepted documents (single docs, or 2-doc blocks). Segment B: only what is
   * left of a 2-doc block that IndexWriter rejected at its first (child) document.
   */
  static void indexScenario(IndexWriter w, boolean acceptedAsBlocks, boolean rejectSecondBlock)
      throws IOException {
    long seq = 0;
    if (acceptedAsBlocks) {
      for (int i = 0; i < ACCEPTED / 2; i++) {
        w.addDocuments(List.of(doc("c" + i, seq++), doc("p" + i, seq++)));
      }
    } else {
      for (int i = 0; i < ACCEPTED; i++) {
        w.addDocument(doc("d" + i, seq++));
      }
    }
    w.commit();
    final long s = seq;
    if (rejectSecondBlock) {
      IllegalArgumentException iae =
          LuceneTestCase.expectThrows(
              IllegalArgumentException.class,
              () -> w.addDocuments(List.of(badChild(s), doc("rejected-parent", s + 1))));
      LuceneTestCase.assertTrue(iae.getMessage(), iae.getMessage().contains("immense term"));
      LuceneTestCase.assertTrue("PRECONDITION: writer open after rejection", w.isOpen());
      LuceneTestCase.assertNull(
          "PRECONDITION: no tragic exception after rejection", w.getTragicException());
    } else {
      w.addDocuments(List.of(doc("c-last", s), doc("p-last", s + 1)));
    }
    w.commit();
  }

  /** Segments as the writer currently sees them (NRT reader keeps segments the MP keeps). */
  static String describe(IndexWriter w) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (DirectoryReader r = DirectoryReader.open(w)) {
      for (LeafReaderContext ctx : r.leaves()) {
        LeafReader lr = ctx.reader();
        sb.append(
            String.format(
                Locale.ROOT,
                "[maxDoc=%d numDocs=%d parentField=%s hasBlocks=%s] ",
                lr.maxDoc(),
                lr.numDocs(),
                lr.getFieldInfos().getParentField(),
                lr.getMetaData().hasBlocks()));
      }
    }
    return sb.toString();
  }

  static String describe(Directory dir) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : r.leaves()) {
        LeafReader lr = ctx.reader();
        sb.append(
            String.format(
                Locale.ROOT,
                "[maxDoc=%d numDocs=%d parentField=%s hasBlocks=%s] ",
                lr.maxDoc(),
                lr.numDocs(),
                lr.getFieldInfos().getParentField(),
                lr.getMetaData().hasBlocks()));
      }
    }
    return sb.toString();
  }

  static int leafCount(IndexWriter w) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(w)) {
      return r.leaves().size();
    }
  }

  /** The documented outcome: forceMerge(1) succeeds and leaves the accepted docs. */
  static void assertForceMergeWorks(IndexWriter w, String label, int expectedLive)
      throws IOException {
    Throwable failure = null;
    try {
      w.forceMerge(1);
    } catch (Throwable t) {
      failure = t;
    }
    if (failure != null) {
      throw new AssertionError(
          "BUG "
              + label
              + ": forceMerge(1) threw "
              + failure
              + "; writer open="
              + w.isOpen()
              + "; tragic="
              + w.getTragicException(),
          failure);
    }
    try (DirectoryReader r = DirectoryReader.open(w)) {
      LuceneTestCase.assertEquals(label + ": live docs after forceMerge", expectedLive, r.numDocs());
      LuceneTestCase.assertEquals(label + ": one segment after forceMerge", 1, r.leaves().size());
    }
  }
}
