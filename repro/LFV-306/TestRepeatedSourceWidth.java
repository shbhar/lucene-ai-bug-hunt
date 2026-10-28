package org.apache.lucene.queries.intervals;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ordered()/unordered() with equal sub-sources are built as a RepeatingIntervalsSource. Its iterator computes
 * width() as the sum of (start - end + 1) over the repeated sub-intervals, i.e. with the sign inverted, so for
 * sub-intervals wider than one position width() is too small and gaps() too large. maxgaps() then drops intervals
 * through the scorer, while the Matches path (DuplicateMatchesIterator, which sums end - start + 1) keeps them.
 */
public class TestRepeatedSourceWidth extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField("f", "new york new york", Field.Store.NO));
      w.addDocument(doc);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  // "new york" twice, back to back: no gap between the two sub-intervals
  static IntervalsSource twice() {
    IntervalsSource ny = Intervals.phrase("new", "york");
    return Intervals.ordered(ny, ny);
  }

  public void testMaxGapsZeroMatches() throws Exception {
    IndexSearcher searcher = newSearcher(reader);
    LeafReaderContext ctx = reader.leaves().get(0);
    Query q = new IntervalQuery("f", Intervals.maxgaps(0, twice()));
    Weight weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
    assertNotNull("Weight#matches reports a match", weight.matches(ctx, 0));
    assertEquals(q.toString() + " on 'new york new york'", 1, searcher.count(q));
  }

  public void testIteratorAndMatchesAgreeOnGaps() throws Exception {
    LeafReaderContext ctx = reader.leaves().get(0);
    IntervalIterator it = twice().intervals("f", ctx);
    assertEquals(0, it.nextDoc());
    assertEquals(0, it.nextInterval());
    assertEquals(3, it.end());
    IntervalMatchesIterator mi = twice().matches("f", ctx, 0);
    assertTrue(mi.next());
    assertEquals("matches width", 4, mi.width());
    assertEquals("matches gaps", 0, mi.gaps());
    assertEquals("iterator width", 4, it.width());
    assertEquals("iterator gaps", 0, it.gaps());
  }
}
