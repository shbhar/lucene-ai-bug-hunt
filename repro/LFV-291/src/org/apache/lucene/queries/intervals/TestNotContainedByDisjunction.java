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
 * Intervals.notContainedBy(small, big) must return the intervals of small that are not inside any interval of big.
 * When big is an OR whose clauses have different lengths, NotContainedByIntervalsSource.build pulls the OR up and
 * returns OR(notContainedBy(small, b1), notContainedBy(small, b2), ...): an interval inside b1 is then still returned
 * by the b2 branch, so the filter excludes nothing that is covered by only one of the clauses.
 */
public class TestNotContainedByDisjunction extends LuceneTestCase {

  public void testOrOfPhrasesAsBigSource() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String text : new String[] {"i love new york", "york city is big", "york is here"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        IntervalsSource york = Intervals.term("york");
        IntervalsSource newYork = Intervals.phrase("new", "york");
        IntervalsSource yorkCity = Intervals.phrase("york", "city");

        // "york" that is neither part of "new york" nor of "york city": only the third document
        IntervalsSource nested = Intervals.notContainedBy(Intervals.notContainedBy(york, newYork), yorkCity);
        IntervalsSource noRewrite = Intervals.notContainedBy(york, Intervals.or(false, newYork, yorkCity));
        IntervalsSource withOr = Intervals.notContainedBy(york, Intervals.or(newYork, yorkCity));

        assertEquals("nested filters", 1, searcher.count(new IntervalQuery("f", nested)));
        assertEquals("or(rewrite=false)", 1, searcher.count(new IntervalQuery("f", noRewrite)));
        assertEquals(
            "notContainedBy(york, or(\"new york\", \"york city\")) rewritten to " + withOr,
            1,
            searcher.count(new IntervalQuery("f", withOr)));
      }
    }
  }

  public void testSmallestCase() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField("f", "c a", Field.Store.NO));
        w.addDocument(doc);
        doc = new Document();
        doc.add(new TextField("f", "b", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        // "a" in "c a" is contained by the interval "c a" of the subtrahend, so nothing may be returned;
        // adding the clause "b" to the subtrahend can only remove intervals, never add them
        IntervalsSource oneClause = Intervals.notContainedBy(Intervals.term("a"), Intervals.phrase("c", "a"));
        IntervalsSource twoClauses =
            Intervals.notContainedBy(
                Intervals.term("a"), Intervals.or(Intervals.phrase("c", "a"), Intervals.term("b")));
        assertEquals(0, searcher.count(new IntervalQuery("f", oneClause)));
        assertEquals("rewritten to " + twoClauses, 0, searcher.count(new IntervalQuery("f", twoClauses)));
      }
    }
  }
}
