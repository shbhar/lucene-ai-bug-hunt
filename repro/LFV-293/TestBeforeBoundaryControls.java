package org.apache.lucene.queries.intervals;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Verifier controls for the claim that Intervals.before treats a source at position 0 as "before" a
 * reference that also starts at position 0. Each document is indexed alone so counts are per case.
 * Controls (expected to pass): strictness at positive positions and true positives. Targets
 * (expected to fail on stock jars): the position-0 boundary through the Java API, raw interval
 * iteration and the flexible query parser.
 */
public class TestBeforeBoundaryControls extends LuceneTestCase {

  private static int count(String text, IntervalsSource q) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(new IntervalQuery("f", q));
      }
    }
  }

  // Controls: strict "before" at positive reference positions, and genuine before-matches.
  public void testControls() throws Exception {
    IntervalsSource york = Intervals.term("york");
    IntervalsSource newT = Intervals.term("new");
    // reference "new york" starts at 1; source "new" at the same start is not before it
    assertEquals(0, count("a new york", Intervals.before(newT, Intervals.phrase("new", "york"))));
    // a single "york" at position 1 is not before itself
    assertEquals(0, count("big york", Intervals.before(york, york)));
    // genuine: the first york is before the second
    assertEquals(1, count("york york", Intervals.before(york, york)));
    // genuine: "new" at 0 is before "york city" at 1
    assertEquals(1, count("new york city", Intervals.before(newT, Intervals.phrase("york", "city"))));
  }

  // Target 1: Java API, reference starts at position 0.
  public void testApiPositionZero() throws Exception {
    assertEquals(
        0,
        count("new york", Intervals.before(Intervals.term("new"), Intervals.phrase("new", "york"))));
  }

  // Target 2: raw iteration reports the source interval [0,0] as a before-match.
  public void testIteratorReportsZeroInterval() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "york", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        LeafReaderContext ctx = reader.leaves().get(0);
        IntervalsSource york = Intervals.term("york");
        IntervalIterator it = Intervals.before(york, york).intervals("f", ctx);
        StringBuilder sb = new StringBuilder();
        if (it != null && it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          for (int s = it.nextInterval();
              s != IntervalIterator.NO_MORE_INTERVALS;
              s = it.nextInterval()) {
            sb.append('[').append(it.start()).append(',').append(it.end()).append(']');
          }
        }
        assertEquals("before(york, york) on 'york' must yield no interval", "", sb.toString());
      }
    }
  }

  // Target 3: StandardQueryParser fn:before delegates to Intervals.before.
  public void testParserPositionZero() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    Query q = qp.parse("fn:before(york york)", "f");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "york", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        assertEquals(q.toString(), 0, new IndexSearcher(reader).count(q));
      }
    }
  }
}
