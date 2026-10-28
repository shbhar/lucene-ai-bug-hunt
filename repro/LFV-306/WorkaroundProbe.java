package org.apache.lucene.queries.intervals;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * Which documents match when the second copy of a repeated phrase is wrapped in a no-op
 * maxwidth(2, ...) filter, so that the copies are no longer equal and not deduplicated.
 */
public class WorkaroundProbe {
  public static void main(String[] args) throws Exception {
    String[] docs = {"new york new york", "new york", "new york x new york"};
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()))) {
        for (String d : docs) {
          Document doc = new Document();
          doc.add(new TextField("f", d, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        IntervalsSource ny = Intervals.phrase("new", "york");
        IntervalsSource wrapped = Intervals.maxwidth(2, ny);
        IntervalsSource[] sources = {
          Intervals.ordered(ny, ny),
          Intervals.ordered(ny, wrapped),
          Intervals.unordered(ny, ny),
          Intervals.unordered(ny, wrapped),
          Intervals.maxgaps(0, Intervals.ordered(ny, ny)),
          Intervals.maxgaps(0, Intervals.ordered(ny, wrapped)),
          Intervals.maxgaps(0, Intervals.unordered(ny, ny)),
          Intervals.maxgaps(0, Intervals.unordered(ny, wrapped)),
        };
        for (IntervalsSource src : sources) {
          StringBuilder sb = new StringBuilder(src.toString()).append(" -> [");
          for (ScoreDoc sd : s.search(new IntervalQuery("f", src), 10).scoreDocs) {
            sb.append(" '").append(docs[sd.doc]).append("'");
          }
          System.out.println(sb.append(" ]"));
        }
      }
    }
  }
}
