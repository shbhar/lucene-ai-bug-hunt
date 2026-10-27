package org.apache.lucene.queries.intervals;

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
 * Intervals.before(source, reference) "returns intervals from the source that appear before intervals from the
 * reference". It is built as containedBy(source, extend(PRECEDING(reference), MAX, 0)); PRECEDING clamps the
 * position before a reference interval that starts at position 0 to 0 (Math.max(0, pos - 1)), so a source interval
 * at position 0 counts as "before" a reference interval that also starts at position 0.
 */
public class TestBeforeAtFirstPosition extends LuceneTestCase {

  public void testNothingIsBeforePositionZero() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        for (String text : new String[] {"new york", "new york city", "york"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource newTerm = Intervals.term("new");
        // control: "new" is before "york city" in "new york city"
        assertEquals(1, searcher.count(new IntervalQuery("f", Intervals.before(newTerm, Intervals.phrase("york", "city")))));
        // no "new" precedes the phrase "new york" in any document
        IntervalsSource q = Intervals.before(newTerm, Intervals.phrase("new", "york"));
        assertEquals(q.toString(), 0, searcher.count(new IntervalQuery("f", q)));
      }
    }
  }

  public void testTermBeforeItself() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField("f", "york", Field.Store.NO));
        w.addDocument(doc);
        doc = new Document();
        doc.add(new TextField("f", "big york", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource york = Intervals.term("york");
        // a single occurrence of "york" is never before another "york"
        assertEquals(0, searcher.count(new IntervalQuery("f", Intervals.before(york, york))));
      }
    }
  }
}
