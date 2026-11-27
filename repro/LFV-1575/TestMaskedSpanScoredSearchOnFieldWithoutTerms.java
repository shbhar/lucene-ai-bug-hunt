import static org.junit.Assert.assertEquals;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.FieldMaskingSpanQuery;
import org.apache.lucene.queries.spans.SpanOrQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class TestMaskedSpanScoredSearchOnFieldWithoutTerms {
  @Test
  public void testScoredSearchReturnsTheMatch() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new TextField("first", "james", Field.Store.NO));
      writer.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        SpanQuery query =
            new SpanOrQuery(
                new FieldMaskingSpanQuery(new SpanTermQuery(new Term("first", "james")), "other"));
        // expected: the one document; actual: NullPointerException (no term is indexed in "other")
        assertEquals(1, new IndexSearcher(reader).search(query, 10).scoreDocs.length);
      }
    }
  }
}
