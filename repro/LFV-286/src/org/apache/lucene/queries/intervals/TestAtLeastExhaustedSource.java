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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.atLeast(k, sources...) with k < sources.length returns "intervals that span combinations of intervals
 * from minShouldMatch of the sources". MinimumShouldMatchIntervalIterator#nextInterval pops the iterator at the
 * current start; when that iterator has no more intervals it is not replaced from the background queue, so the
 * iteration stops although other sources still have intervals. Iterators that wait in the background queue are
 * also never advanced past the current start, so when one is pulled back it can produce an interval that starts
 * before the one just returned (non-minimal and out of order).
 */
public class TestAtLeastExhaustedSource extends LuceneTestCase {

  static List<String> intervals(IntervalsSource source, IndexReader reader) throws Exception {
    List<String> out = new ArrayList<>();
    LeafReaderContext ctx = reader.leaves().get(0);
    IntervalIterator it = source.intervals("f", ctx);
    if (it == null || it.nextDoc() != 0) return out;
    while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
      out.add("[" + it.start() + "," + it.end() + "]");
    }
    return out;
  }

  static IndexReader index(Directory dir, String text) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField("f", text, Field.Store.NO));
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  public void testAllIntervalsOfAtLeastOne() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "a b")) {
      IntervalsSource atLeastOne = Intervals.atLeast(1, Intervals.term("a"), Intervals.term("b"));
      // same as or(a, b)
      assertEquals(List.of("[0,0]", "[1,1]"), intervals(Intervals.or(Intervals.term("a"), Intervals.term("b")), reader));
      assertEquals(List.of("[0,0]", "[1,1]"), intervals(atLeastOne, reader));
    }
  }

  public void testMissedMatchInsideOrdered() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "a c b")) {
      IndexSearcher searcher = newSearcher(reader);
      IntervalsSource withOr =
          Intervals.ordered(Intervals.term("c"), Intervals.or(Intervals.term("a"), Intervals.term("b")));
      IntervalsSource withAtLeast =
          Intervals.ordered(Intervals.term("c"), Intervals.atLeast(1, Intervals.term("a"), Intervals.term("b")));
      assertEquals(1, searcher.count(new IntervalQuery("f", withOr)));
      // "c" at 1 is followed by "b" at 2
      assertEquals(withAtLeast.toString(), 1, searcher.count(new IntervalQuery("f", withAtLeast)));
    }
  }

  public void testStaleSourceReentersOutOfOrder() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, "a b b")) {
      IntervalsSource b = Intervals.term("b");
      IntervalsSource ab = Intervals.phrase("a", "b");
      // minimal intervals with one of {b, "a b"}: the two b's ("a b" = [0,1] contains [1,1])
      assertEquals(List.of("[1,1]", "[2,2]"), intervals(Intervals.or(b, ab), reader));
      assertEquals(List.of("[1,1]", "[2,2]"), intervals(Intervals.atLeast(1, b, ab), reader));
    }
  }
}
