package org.apache.lucene.queries.intervals;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRepeatedPhraseMaxGaps extends LuceneTestCase {

  public void testMaxGapsZeroOverRepeatedPhrase() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "new york new york", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IntervalsSource ny = Intervals.phrase("new", "york");
        IntervalQuery q = new IntervalQuery("f", Intervals.maxgaps(0, Intervals.unordered(ny, ny)));
        // the two "new york" intervals are back to back (0 gaps): expected 1 hit, actual 0
        assertEquals(q + " on 'new york new york'", 1, new IndexSearcher(reader).count(q));
      }
    }
  }
}
