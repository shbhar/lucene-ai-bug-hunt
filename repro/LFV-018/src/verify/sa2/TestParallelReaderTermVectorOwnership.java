package verify.sa2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.ParallelCompositeReader;
import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.index.SlowCodecReaderWrapper;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Independent verification of the finding: does ParallelLeafReader.termVectors() still follow
 * the "first reader having a given field wins" rule that getFieldInfos()/terms()/doc values use?
 *
 * <p>All indexes are one segment with one document, built with the default IndexWriterConfig
 * (StandardAnalyzer) on ByteBuffersDirectory, so the only source of non-determinism left in the
 * code under test is IdentityHashMap iteration order, which testFirstReaderWinsInBothOrders
 * neutralises by checking both reader orders.
 */
@SuppressSysoutChecks(bugUrl = "prints observations for the verification write-up")
public class TestParallelReaderTermVectorOwnership extends LuceneTestCase {

  private static final FieldType NO_TV = TextField.TYPE_NOT_STORED;
  private static final FieldType TV = new FieldType(TextField.TYPE_NOT_STORED);

  static {
    TV.setStoreTermVectors(true);
    TV.freeze();
  }

  /** One segment, one document; args are (fieldName, value, FieldType) triples. */
  private static DirectoryReader oneDocIndex(Directory dir, Object... triples) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      for (int i = 0; i < triples.length; i += 3) {
        doc.add(new Field((String) triples[i], (String) triples[i + 1], (FieldType) triples[i + 2]));
      }
      w.addDocument(doc);
      w.commit();
    }
    DirectoryReader r = DirectoryReader.open(dir);
    assertEquals(1, r.leaves().size());
    return r;
  }

  private static String singleTerm(Terms terms) throws IOException {
    if (terms == null) {
      return null;
    }
    TermsEnum te = terms.iterator();
    BytesRef first = te.next();
    assertNotNull(first);
    String s = first.utf8ToString();
    assertNull("expected exactly one term", te.next());
    return s;
  }

  private static String termVectorTerm(IndexReader r, int doc, String field) throws IOException {
    Fields fields = r.termVectors().get(doc);
    return fields == null ? null : singleTerm(fields.terms(field));
  }

  /**
   * The first (owning) reader indexes "body" without term vectors, the second one with. The
   * parallel reader's FieldInfos and postings come from the owner, so it must not expose a term
   * vector for "body".
   */
  public void testOwnerWithoutVectorsLeafReader() throws Exception {
    try (Directory d1 = new ByteBuffersDirectory();
        Directory d2 = new ByteBuffersDirectory();
        DirectoryReader owner = oneDocIndex(d1, "body", "owner", NO_TV);
        DirectoryReader later = oneDocIndex(d2, "body", "intruder", TV);
        ParallelLeafReader plr =
            new ParallelLeafReader(false, getOnlyLeafReader(owner), getOnlyLeafReader(later))) {
      FieldInfo fi = plr.getFieldInfos().fieldInfo("body");
      String postings = singleTerm(plr.terms("body"));
      String tv = termVectorTerm(plr, 0, "body");
      System.out.println(
          "[leaf] getFieldInfos().fieldInfo(body).hasTermVectors()="
              + fi.hasTermVectors()
              + " getFieldInfos().hasTermVectors()="
              + plr.getFieldInfos().hasTermVectors()
              + " terms(body)="
              + postings
              + " termVectors().get(0).terms(body)="
              + tv);
      assertFalse(fi.hasTermVectors());
      assertEquals("owner", postings);
      assertNull("term vector for 'body' came from the non-owning (second) reader", tv);
    }
  }

  /** Same as above through the public composite API. */
  public void testOwnerWithoutVectorsCompositeReader() throws Exception {
    try (Directory d1 = new ByteBuffersDirectory();
        Directory d2 = new ByteBuffersDirectory();
        DirectoryReader owner = oneDocIndex(d1, "body", "owner", NO_TV);
        DirectoryReader later = oneDocIndex(d2, "body", "intruder", TV);
        ParallelCompositeReader pcr = new ParallelCompositeReader(false, owner, later)) {
      String tv = termVectorTerm(pcr, 0, "body");
      System.out.println(
          "[composite] leaf fieldInfo(body).hasTermVectors()="
              + pcr.leaves().get(0).reader().getFieldInfos().fieldInfo("body").hasTermVectors()
              + " termVectors().get(0).terms(body)="
              + tv);
      assertNull("term vector for 'body' came from the non-owning (second) reader", tv);
    }
  }

  /**
   * Both readers store term vectors for "body". For each pair (a, b) build PLR(a, b) and PLR(b, a):
   * "first reader wins" requires a's vector in the first and b's in the second. If the result
   * depended only on the identity of the readers (IdentityHashMap order), at least one order is
   * wrong. Several independently opened pairs are checked.
   */
  public void testFirstReaderWinsInBothOrders() throws Exception {
    final int pairs = 8;
    int violations = 0;
    StringBuilder log = new StringBuilder();
    for (int p = 0; p < pairs; p++) {
      try (Directory da = new ByteBuffersDirectory();
          Directory db = new ByteBuffersDirectory();
          DirectoryReader a = oneDocIndex(da, "body", "alpha", TV);
          DirectoryReader b = oneDocIndex(db, "body", "beta", TV)) {
        LeafReader la = getOnlyLeafReader(a);
        LeafReader lb = getOnlyLeafReader(b);
        try (ParallelLeafReader ab = new ParallelLeafReader(false, la, lb);
            ParallelLeafReader ba = new ParallelLeafReader(false, lb, la)) {
          assertEquals("alpha", singleTerm(ab.terms("body")));
          assertEquals("beta", singleTerm(ba.terms("body")));
          String tvAB = termVectorTerm(ab, 0, "body");
          String tvBA = termVectorTerm(ba, 0, "body");
          boolean ok = "alpha".equals(tvAB) && "beta".equals(tvBA);
          if (ok == false) {
            violations++;
          }
          log.append(
              String.format(
                  "[both-orders] pair %d: PLR(a,b) tv=%s (want alpha)  PLR(b,a) tv=%s (want beta)  %s%n",
                  p, tvAB, tvBA, ok ? "OK" : "VIOLATION"));
        }
      }
    }
    System.out.print(log);
    assertEquals(
        "pairs (of " + pairs + ") where a term vector did not come from the first reader",
        0,
        violations);
  }

  /**
   * Both readers vector "body" with different content. CheckIndex's slow checks cross-check each
   * term vector term against the postings of the same reader; check both orders.
   */
  public void testCheckReaderBothVectoredBothOrders() throws Exception {
    try (Directory da = new ByteBuffersDirectory();
        Directory db = new ByteBuffersDirectory();
        DirectoryReader a = oneDocIndex(da, "body", "alpha", TV);
        DirectoryReader b = oneDocIndex(db, "body", "beta", TV)) {
      LeafReader la = getOnlyLeafReader(a);
      LeafReader lb = getOnlyLeafReader(b);
      int failures = 0;
      for (LeafReader[] order : new LeafReader[][] {{la, lb}, {lb, la}}) {
        try (ParallelLeafReader plr = new ParallelLeafReader(false, order)) {
          try {
            TestUtil.checkReader(plr);
            System.out.println("[checkReader-both] order " + (order[0] == la ? "(a,b)" : "(b,a)") + " clean");
          } catch (RuntimeException e) {
            failures++;
            System.out.println(
                "[checkReader-both] order " + (order[0] == la ? "(a,b)" : "(b,a)") + " threw " + e);
          }
        }
      }
      assertEquals("orders rejected by checkReader", 0, failures);
    }
  }

  /** Lucene's own reader consistency check (CheckIndex via SlowCodecReaderWrapper). */
  public void testCheckReaderAcceptsParallelLeafReader() throws Exception {
    try (Directory d1 = new ByteBuffersDirectory();
        Directory d2 = new ByteBuffersDirectory();
        DirectoryReader owner = oneDocIndex(d1, "body", "owner", NO_TV);
        DirectoryReader later = oneDocIndex(d2, "body", "intruder", TV);
        ParallelLeafReader plr =
            new ParallelLeafReader(false, getOnlyLeafReader(owner), getOnlyLeafReader(later))) {
      try {
        TestUtil.checkReader(plr);
        System.out.println("[checkReader] clean");
      } catch (RuntimeException e) {
        System.out.println("[checkReader] threw " + e);
        throw new AssertionError("TestUtil.checkReader rejected the ParallelLeafReader", e);
      }
    }
  }

  /**
   * addIndexes(SlowCodecReaderWrapper.wrap(plr)): the owner has "body" without vectors plus a
   * vectored "title" (so the merged segment has a term vectors file). The new segment must not
   * contain vectors for "body", and CheckIndex must be clean.
   */
  public void testAddIndexesOfParallelLeafReader() throws Exception {
    try (Directory d1 = new ByteBuffersDirectory();
        Directory d2 = new ByteBuffersDirectory();
        Directory dest = new ByteBuffersDirectory();
        DirectoryReader owner = oneDocIndex(d1, "body", "owner", NO_TV, "title", "heading", TV);
        DirectoryReader later = oneDocIndex(d2, "body", "intruder", TV);
        ParallelLeafReader plr =
            new ParallelLeafReader(false, getOnlyLeafReader(owner), getOnlyLeafReader(later))) {
      try (IndexWriter w = new IndexWriter(dest, new IndexWriterConfig())) {
        w.addIndexes(SlowCodecReaderWrapper.wrap(plr));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dest)) {
        LeafReader leaf = getOnlyLeafReader(r);
        System.out.println(
            "[addIndexes] dest fieldInfo(body).hasTermVectors()="
                + leaf.getFieldInfos().fieldInfo("body").hasTermVectors()
                + " dest tv(body)="
                + termVectorTerm(r, 0, "body")
                + " dest tv(title)="
                + termVectorTerm(r, 0, "title"));
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      CheckIndex.Status status;
      try (CheckIndex ci = new CheckIndex(dest)) {
        ci.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8));
        ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS);
        status = ci.checkIndex();
      }
      for (String line : bos.toString(StandardCharsets.UTF_8).split("\n")) {
        if (line.contains("term vectors") || line.contains("ERROR") || line.contains("WARNING")) {
          System.out.println("[addIndexes] CheckIndex: " + line.trim());
        }
      }
      System.out.println("[addIndexes] CheckIndex clean=" + status.clean);
      assertTrue("CheckIndex reports the addIndexes() result as broken", status.clean);
    }
  }
}
