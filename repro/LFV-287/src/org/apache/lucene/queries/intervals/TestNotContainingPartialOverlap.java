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
 * Intervals.notContaining(minuend, subtrahend) must return the minuend intervals that do not contain a subtrahend
 * interval. NotContainingIterator also drops a minuend interval that only partially overlaps a subtrahend interval
 * (it keeps an interval only if the next subtrahend interval starts after its end), so every minuend interval
 * vanishes from both containing() and notContaining().
 */
public class TestNotContainingPartialOverlap extends LuceneTestCase {

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

  public void testPartialOverlapIsNotContainment() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField("f", "a b c", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource ab = Intervals.phrase("a", "b"); // [0,1]
        IntervalsSource bc = Intervals.phrase("b", "c"); // [1,2]: overlaps [0,1] but is not inside it
        assertEquals(List.of("[0,1]"), intervals(ab, reader));
        assertEquals(List.of(), intervals(Intervals.containing(ab, bc), reader));
        assertEquals(
            "\"a b\" does not contain \"b c\"",
            List.of("[0,1]"),
            intervals(Intervals.notContaining(ab, bc), reader));
        assertEquals(1, searcher.count(new IntervalQuery("f", Intervals.notContaining(ab, bc))));
      }
    }
  }

  public void testTermAgainstPhrase() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField("f", "new york", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        // a single-position interval can never contain the two-position interval "new york"
        IntervalsSource york = Intervals.term("york");
        IntervalsSource newYork = Intervals.phrase("new", "york");
        assertEquals(List.of("[1,1]"), intervals(Intervals.notContaining(york, newYork), reader));
      }
    }
  }
}
