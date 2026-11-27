import static org.junit.Assert.assertEquals;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSelector;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class TestSortedTopHitsOnDocumentBlocks {
  @Test
  public void testTopTwoByIndexSortFieldWhenChildrenMatch() throws Exception {
    // Expected: the top 2 by ts are the parents, 1 and 2. Actual on 10.6.0: 1 and a child.
    Sort byTs =
        new Sort(
            new SortedNumericSortField(
                "ts", SortField.Type.LONG, false, SortedNumericSelector.Type.MIN, Long.MAX_VALUE));
    IndexWriterConfig config = new IndexWriterConfig().setIndexSort(byTs).setParentField("_parent");
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, config)) {
      for (long ts : new long[] {1, 2}) {
        Document parent = new Document();
        parent.add(new SortedNumericDocValuesField("ts", ts));
        writer.addDocuments(List.of(new Document(), parent));
      }
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        TopDocs top = new IndexSearcher(reader).search(MatchAllDocsQuery.INSTANCE, 2, byTs);
        assertEquals(
            "second smallest ts (Long.MAX_VALUE = a child without a value)",
            2L,
            ((FieldDoc) top.scoreDocs[1]).fields[0]);
      }
    }
  }
}
