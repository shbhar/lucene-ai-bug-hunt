package verify.intervals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.intervals.IntervalIterator;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.ordered(...) requires its sub-source intervals to appear in order without overlapping
 * (queryparser fn:ordered docs: "Source intervals must not overlap"; example
 * fn:ordered(fn:phrase(brown fox) fn:phrase(fox jumps)) has no matches). This must not depend on
 * whether two neighbouring sub-sources happen to be equal objects.
 *
 * <p>Each "control" test uses a sub-source that is logically equivalent but not equal (or(X, term
 * that does not occur)), which keeps the generic ordered iterator; each "bug" test uses two equal
 * sub-sources. Public API only.
 */
public class TestOrderedEqualSourcesMustNotOverlap extends LuceneTestCase {

  private static final String F = "f";
  private static final String ABSENT = "zzz"; // never occurs in any document

  // ---- helpers -------------------------------------------------------------------------------

  /** Indexes one document with whitespace tokenisation and returns the result of the probe. */
  private interface Probe<T> {
    T run(IndexSearcher searcher, LeafReaderContext leaf) throws IOException;
  }

  private <T> T withDoc(String text, Probe<T> probe) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new TextField(F, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        return probe.run(new IndexSearcher(r), r.leaves().get(0));
      }
    }
  }

  private int hits(String text, IntervalsSource src) throws IOException {
    return withDoc(text, (s, leaf) -> s.count(new IntervalQuery(F, src)));
  }

  /** All intervals that the source's iterator reports on doc 0, as "[start,end]" strings. */
  private List<String> intervals(String text, IntervalsSource src) throws IOException {
    return withDoc(
        text,
        (s, leaf) -> {
          List<String> out = new ArrayList<>();
          IntervalIterator it = src.intervals(F, leaf);
          if (it == null || it.nextDoc() != 0) {
            return out;
          }
          while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
            out.add("[" + it.start() + "," + it.end() + "]");
          }
          return out;
        });
  }

  private boolean hasMatches(String text, IntervalsSource src) throws IOException {
    return withDoc(
        text,
        (s, leaf) -> {
          Query q = s.rewrite(new IntervalQuery(F, src));
          Weight w = s.createWeight(q, ScoreMode.COMPLETE_NO_SCORES, 1f);
          Matches m = w.matches(leaf, 0);
          return m != null;
        });
  }

  private static IntervalsSource aa() {
    return Intervals.phrase(Intervals.term("a"), Intervals.term("a"));
  }

  private static IntervalsSource ab() {
    return Intervals.unordered(Intervals.term("a"), Intervals.term("b"));
  }

  private static IntervalsSource orAbsent(IntervalsSource s) {
    return Intervals.or(s, Intervals.term(ABSENT));
  }

  // ---- controls: an equivalent but unequal second sub-source -------------------------------

  public void testControlPhraseThenEquivalentPhrase() throws IOException {
    // 'a a a' holds [0,1] and [1,2] of "a a", which overlap: no ordered, non-overlapping pair.
    assertEquals(0, hits("a a a", Intervals.ordered(aa(), orAbsent(aa()))));
    // 'a a a a' holds [0,1] then [2,3]: one minimal interval [0,3].
    assertEquals(1, hits("a a a a", Intervals.ordered(aa(), orAbsent(aa()))));
    assertEquals(
        Arrays.asList("[0,3]"), intervals("a a a a", Intervals.ordered(aa(), orAbsent(aa()))));
  }

  public void testControlFlatTerms() throws IOException {
    // ordered(phrase(a,a), phrase(a,a)) needs the same positions as ordered(a,a,a,a).
    IntervalsSource a = Intervals.term("a");
    assertEquals(0, hits("a a a", Intervals.ordered(a, a, a, a)));
    assertEquals(1, hits("a a a a", Intervals.ordered(a, a, a, a)));
  }

  public void testControlUnorderedPairThenEquivalent() throws IOException {
    assertEquals(0, hits("a b a", Intervals.ordered(ab(), orAbsent(ab()))));
    assertEquals(1, hits("a b a b", Intervals.ordered(ab(), orAbsent(ab()))));
  }

  public void testControlDocumentedOverlapExample() throws IOException {
    // The documented fn:ordered example with unequal overlapping phrases: no match.
    IntervalsSource brownFox = Intervals.phrase("brown", "fox");
    IntervalsSource foxJumps = Intervals.phrase("fox", "jumps");
    assertEquals(0, hits("the quick brown fox jumps", Intervals.ordered(brownFox, foxJumps)));
    assertEquals(
        0, hits("the quick brown fox jumps", Intervals.unorderedNoOverlaps(brownFox, foxJumps)));
  }

  // ---- the claim: two equal sub-sources ----------------------------------------------------

  public void testBugSamePhraseTwiceHits() throws IOException {
    assertEquals("ordered(P, P) on 'a a a'", 0, hits("a a a", Intervals.ordered(aa(), aa())));
  }

  public void testBugSamePhraseTwiceIterator() throws IOException {
    assertEquals(
        "intervals of ordered(P, P) on 'a a a'",
        Arrays.asList(),
        intervals("a a a", Intervals.ordered(aa(), aa())));
  }

  public void testBugSamePhraseTwiceIntervalsOnMatchingDoc() throws IOException {
    // The document does match, but the reported intervals must be the non-overlapping one.
    assertEquals(
        "intervals of ordered(P, P) on 'a a a a'",
        Arrays.asList("[0,3]"),
        intervals("a a a a", Intervals.ordered(aa(), aa())));
  }

  public void testBugSamePhraseTwiceMatches() throws IOException {
    assertFalse(
        "Weight#matches of ordered(P, P) on 'a a a'",
        hasMatches("a a a", Intervals.ordered(aa(), aa())));
  }

  public void testBugSameUnorderedPairTwice() throws IOException {
    assertEquals("ordered(U, U) on 'a b a'", 0, hits("a b a", Intervals.ordered(ab(), ab())));
  }

  public void testBugUnorderedNoOverlapsSamePhrase() throws IOException {
    assertEquals(
        "unorderedNoOverlaps(P, P) on 'a a a'",
        0,
        hits("a a a", Intervals.unorderedNoOverlaps(aa(), aa())));
  }

  public void testBugNestedOrderedTwice() throws IOException {
    IntervalsSource a = Intervals.term("a");
    assertEquals(
        "ordered(ordered(a,a), ordered(a,a)) on 'a a a'",
        0,
        hits("a a a", Intervals.ordered(Intervals.ordered(a, a), Intervals.ordered(a, a))));
  }
}
