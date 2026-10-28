package repro.v1;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.intervals.IntervalIterator;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, outside the intervals package) of the claim that
 * Intervals.ordered(a, a) / Intervals.unordered(a, a) report minExtent() 1 although none of their
 * intervals is narrower than 2 positions, and that a gap-sensitive parent (phrase, maxgaps) then
 * fails to pull up a disjunction that contains them.
 */
public class TestRepeatMinExtent extends LuceneTestCase {

  private static final String F = "body";
  private static final String[] DOCS = {
    "x a a c", // 0: target for the repeated source
    "x a b c", // 1: target for the unequal control
    "x a c", //   2: single-position alternative
    "a a a a b a b" // 3: many repeats, for measuring interval widths
  };

  private static Directory dir;
  private static DirectoryReader reader;
  private static IndexSearcher searcher;

  private static IntervalsSource t(String s) {
    return Intervals.term(s);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < DOCS.length; i++) {
        Document d = new Document();
        d.add(new StoredField("id", i));
        d.add(new TextField(F, DOCS[i], Field.Store.NO));
        w.addDocument(d);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    reader = null;
    searcher = null;
    dir = null;
    super.tearDown();
  }

  /** Ids (DOCS index) of documents matched by an IntervalQuery over the source. */
  private static Set<Integer> hits(IntervalsSource src) throws IOException {
    Set<Integer> ids = new TreeSet<>();
    for (ScoreDoc sd : searcher.search(new IntervalQuery(F, src), 100).scoreDocs) {
      ids.add(searcher.storedFields().document(sd.doc).getField("id").numericValue().intValue());
    }
    return ids;
  }

  /** Smallest end - start + 1 over every interval the source returns in the whole index. */
  private static int observedMinWidth(IntervalsSource src) throws IOException {
    int min = Integer.MAX_VALUE;
    for (LeafReaderContext ctx : reader.leaves()) {
      IntervalIterator it = src.intervals(F, ctx);
      if (it == null) {
        continue;
      }
      while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
          min = Math.min(min, it.end() - it.start() + 1);
        }
      }
    }
    return min;
  }

  private static Set<Integer> set(Integer... ids) {
    return new TreeSet<>(java.util.Arrays.asList(ids));
  }

  // --- 1. minExtent contract: "the minimum possible width of an interval returned by this source"

  public void testMinExtentControlsUnequalArgs() throws IOException {
    assertEquals(2, observedMinWidth(Intervals.ordered(t("a"), t("b"))));
    assertEquals(2, Intervals.ordered(t("a"), t("b")).minExtent());
    assertEquals(2, Intervals.unordered(t("a"), t("b")).minExtent());
  }

  public void testMinExtentOrderedRepeat() throws IOException {
    IntervalsSource src = Intervals.ordered(t("a"), t("a"));
    // premise: no interval of ordered(a, a) is narrower than 2 positions
    assertEquals("observed minimum width of " + src, 2, observedMinWidth(src));
    assertEquals("minExtent() of " + src, 2, src.minExtent());
  }

  public void testMinExtentUnorderedRepeat() throws IOException {
    IntervalsSource src = Intervals.unordered(t("a"), t("a"));
    assertEquals("observed minimum width of " + src, 2, observedMinWidth(src));
    assertEquals("minExtent() of " + src, 2, src.minExtent());
  }

  // --- 2. consequence: the gap-sensitive disjunction rewrite (Intervals class javadoc) is skipped

  public void testControlsPhraseWithoutOr() throws IOException {
    assertEquals(set(0), hits(Intervals.phrase(t("x"), Intervals.ordered(t("a"), t("a")), t("c"))));
    assertEquals(
        set(0), hits(Intervals.phrase(t("x"), Intervals.unordered(t("a"), t("a")), t("c"))));
    assertEquals(set(2), hits(Intervals.phrase(t("x"), t("a"), t("c"))));
  }

  public void testControlsPhraseOrUnequalArgs() throws IOException {
    assertEquals(
        set(1, 2),
        hits(
            Intervals.phrase(
                t("x"), Intervals.or(Intervals.ordered(t("a"), t("b")), t("a")), t("c"))));
    assertEquals(
        set(1, 2),
        hits(
            Intervals.phrase(
                t("x"), Intervals.or(Intervals.unordered(t("a"), t("b")), t("a")), t("c"))));
  }

  public void testPhraseOrOrderedRepeat() throws IOException {
    IntervalsSource q =
        Intervals.phrase(t("x"), Intervals.or(Intervals.ordered(t("a"), t("a")), t("a")), t("c"));
    // doc 0 'x a a c' matches phrase(x, ordered(a, a), c); doc 2 'x a c' matches phrase(x, a, c)
    assertEquals(q.toString(), set(0, 2), hits(q));
  }

  public void testPhraseOrUnorderedRepeat() throws IOException {
    IntervalsSource q =
        Intervals.phrase(
            t("x"), Intervals.or(Intervals.unordered(t("a"), t("a")), t("a")), t("c"));
    assertEquals(q.toString(), set(0, 2), hits(q));
  }

  public void testMaxGapsOrOrderedRepeat() throws IOException {
    // control: without the or(), ordered(x, ordered(a, a), c) has a 0-gap interval on doc 0
    assertEquals(
        set(0),
        hits(
            Intervals.maxgaps(
                0, Intervals.ordered(t("x"), Intervals.ordered(t("a"), t("a")), t("c")))));
    IntervalsSource q =
        Intervals.maxgaps(
            0,
            Intervals.ordered(
                t("x"), Intervals.or(Intervals.ordered(t("a"), t("a")), t("a")), t("c")));
    assertEquals(q.toString(), set(0, 2), hits(q));
  }
}
