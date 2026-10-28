package org.apache.lucene.queries.intervals;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * V3 intent probes for Intervals.before at position 0, public API only.
 *
 * <p>(1) shift invariance: prepending one unrelated token must not change whether before() matches;
 * (2) LUCENE-8629 describes before as "similar to an ordered near, but returning only intervals from
 * 'a'", so the doc-level match set of before(a, b) is compared with ordered(a, b);
 * (3) mirror: after() at the field's last position.
 */
public class TestBeforeIntent extends LuceneTestCase {

  private static final String[] TEXTS = {
    "new york", "zz new york", "york", "zz york", "york new", "zz york new"
  };

  private int count(IndexSearcher s, IntervalsSource src) throws Exception {
    return s.count(new IntervalQuery("f", src));
  }

  private IndexSearcher searcher(Directory dir, String text) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField("f", text, Field.Store.NO));
      w.addDocument(doc);
    }
    IndexReader r = DirectoryReader.open(dir);
    return new IndexSearcher(r);
  }

  public void testShiftInvarianceAndOrderedAgreement() throws Exception {
    IntervalsSource[][] pairs = {
      {Intervals.term("new"), Intervals.phrase("new", "york")},
      {Intervals.term("york"), Intervals.term("york")},
      {Intervals.term("new"), Intervals.term("york")},
      {Intervals.term("york"), Intervals.term("new")},
    };
    List<String> report = new ArrayList<>();
    boolean shiftOk = true, orderedOk = true, afterMirrorOk = true;
    for (String text : TEXTS) {
      for (IntervalsSource[] p : pairs) {
        int before, ordered, beforeShifted, after, afterShifted;
        try (Directory d1 = newDirectory(); Directory d2 = newDirectory()) {
          IndexSearcher s1 = searcher(d1, text);
          IndexSearcher s2 = searcher(d2, "qq " + text);
          before = count(s1, Intervals.before(p[0], p[1]));
          ordered = count(s1, Intervals.ordered(p[0], p[1]));
          beforeShifted = count(s2, Intervals.before(p[0], p[1]));
          // mirror: after(b-role swapped) -- source after reference
          after = count(s1, Intervals.after(p[1], p[0]));
          afterShifted = count(s2, Intervals.after(p[1], p[0]));
          s1.getIndexReader().close();
          s2.getIndexReader().close();
        }
        String line =
            String.format(
                java.util.Locale.ROOT,
                "doc='%s' before(%s,%s)=%d shifted=%d ordered=%d | after(%s,%s)=%d shifted=%d",
                text, p[0], p[1], before, beforeShifted, ordered, p[1], p[0], after, afterShifted);
        report.add(line);
        System.out.println(line);
        if (before != beforeShifted) shiftOk = false;
        if (before != ordered) orderedOk = false;
        if (after != afterShifted) afterMirrorOk = false;
      }
    }
    System.out.println("shiftOk=" + shiftOk + " orderedOk=" + orderedOk + " afterShiftOk=" + afterMirrorOk);
    assertTrue("after() is shift invariant (control)", afterMirrorOk);
    assertTrue("before() must be shift invariant", shiftOk);
    assertTrue("before() doc-level matches must equal ordered()", orderedOk);
  }

  /** after() at the last position of the field: the FOLLOWING mirror does not clamp. */
  public void testAfterAtLastPositionControl() throws Exception {
    try (Directory d = newDirectory()) {
      IndexSearcher s = searcher(d, "new york");
      assertEquals(0, count(s, Intervals.after(Intervals.term("york"), Intervals.term("york"))));
      assertEquals(
          0, count(s, Intervals.after(Intervals.term("york"), Intervals.phrase("new", "york"))));
      s.getIndexReader().close();
    }
  }
}
