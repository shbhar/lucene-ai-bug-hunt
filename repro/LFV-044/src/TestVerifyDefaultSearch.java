import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NumericUtils;

/** Plain IndexSearcher#search(query, 10) and #count on a skip-indexed latitude field. */
public class TestVerifyDefaultSearch extends LuceneTestCase {
  public void testDefaultSearch() throws Exception {
    Random rnd = new Random(7);
    int n = 100_000, expected = 0;
    long lo = NumericUtils.doubleToSortableLong(45.0), hi = NumericUtils.doubleToSortableLong(90.0);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < n; i++) {
          double lat = -90 + 180 * rnd.nextDouble();
          if (lat >= 45.0) expected++;
          Document d = new Document();
          d.add(NumericDocValuesField.indexedField("lat", NumericUtils.doubleToSortableLong(lat)));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query q = NumericDocValuesField.newSlowRangeQuery("lat", lo, hi);
        TopDocs td = s.search(q, 10);
        Query filtered =
            new BooleanQuery.Builder()
                .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                .add(q, BooleanClause.Occur.FILTER)
                .build();
        TopDocs td2 = s.search(filtered, 10);
        int count = s.count(q);
        System.out.println(
            "expected=" + expected + " count()=" + count + " search(q,10).totalHits=" + td.totalHits
                + " scoreDocs=" + td.scoreDocs.length + " | MUST(*:*)+FILTER(q) totalHits="
                + td2.totalHits + " scoreDocs=" + td2.scoreDocs.length);
        assertEquals(expected, count);
        assertEquals(Math.min(10, expected), td.scoreDocs.length);
        assertEquals(Math.min(10, expected), td2.scoreDocs.length);
      }
    }
  }
}
