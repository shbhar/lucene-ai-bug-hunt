package org.apache.lucene.queries.spans;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpanNearMaskedClausesAcrossSegments extends LuceneTestCase {

  // expected: both documents match, in two segments as in one (2 and 2); actual: 1, then 2
  public void testSameHitsBeforeAndAfterMerge() throws Exception {
    SpanQuery q1 = new FieldMaskingSpanQuery(new SpanTermQuery(new Term("first", "james")), "id");
    SpanQuery q2 = new FieldMaskingSpanQuery(new SpanTermQuery(new Term("last", "jones")), "id");
    Query near = new SpanNearQuery(new SpanQuery[] {q1, q2}, -1, false);

    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new TextField("first", "james", Field.Store.NO));
      doc.add(new TextField("last", "jones", Field.Store.NO));
      doc.add(new TextField("id", "1", Field.Store.NO));
      w.addDocument(doc);
      w.flush();
      doc.removeField("id");
      w.addDocument(doc);

      int twoSegments = count(w, near);
      w.forceMerge(1);
      int oneSegment = count(w, near);

      assertEquals("both documents match in one segment", 2, oneSegment);
      assertEquals("the same documents in two segments", oneSegment, twoSegments);
    }
  }

  private static int count(IndexWriter w, Query query) throws IOException {
    try (DirectoryReader reader = DirectoryReader.open(w)) {
      return new IndexSearcher(reader).count(query);
    }
  }
}
