package org.apache.lucene.search.join;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestRejectedBlockJoin extends LuceneTestCase {

  private static Document doc(String field, String value) {
    Document doc = new Document();
    doc.add(new StringField(field, value, Field.Store.NO));
    return doc;
  }

  public void testRejectedBlockChildIsNotJoinedToNextParent() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      Document immense = new Document();
      immense.add(
          new StringField(
              "big", new BytesRef(new byte[IndexWriter.MAX_TERM_LENGTH + 1]), Field.Store.NO));
      expectThrows(
          IllegalArgumentException.class,
          () -> w.addDocuments(List.of(doc("color", "red"), immense, doc("type", "parent"))));
      w.addDocuments(List.of(doc("color", "blue"), doc("type", "parent")));

      try (DirectoryReader reader = DirectoryReader.open(w)) {
        ToParentBlockJoinQuery query =
            new ToParentBlockJoinQuery(
                new TermQuery(new Term("color", "red")),
                new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))),
                ScoreMode.None);
        // expected 0: the only red child was in the rejected block; actual 1 (the next parent)
        assertEquals(0, new IndexSearcher(reader).count(query));
      }
    }
  }
}
