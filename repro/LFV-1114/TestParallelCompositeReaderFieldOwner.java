package org.apache.lucene.index;

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParallelCompositeReaderFieldOwner extends LuceneTestCase {

  private static Directory index(String... tValues) throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < tValues.length; i++) {
        Document doc = new Document();
        if (tValues[i] != null) {
          doc.add(new StringField("t", tValues[i], Field.Store.NO));
        }
        w.addDocument(doc);
        if (i == 1) {
          w.commit();
        }
      }
    }
    return dir;
  }

  public void testFirstIndexOwnsSharedField() throws Exception {
    try (Directory first = index("new", null, null);
        Directory second = index("old", "old", "old");
        ParallelCompositeReader reader =
            new ParallelCompositeReader(DirectoryReader.open(first), DirectoryReader.open(second))) {
      List<Integer> hits =
          Arrays.stream(new IndexSearcher(reader).search(new TermQuery(new Term("t", "old")), 10).scoreDocs)
              .map(sd -> sd.doc)
              .toList();
      // expected: no hits (the first index owns "t"); actual: [2], the second index's "t" leaks into segment [2]
      assertEquals(List.of(), hits);
    }
  }
}
