import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** End-to-end: the parse difference changes search results of RegexpQuery and classic QP. */
public class TestEscapedRangeStartSearch extends LuceneTestCase {

  private static final String[] VALUES = {"a", "m", "z", ".", "-", "/"};

  // values in '.'..'z' : a m z . /   (5 docs);  '-' (0x2D) is below '.' (0x2E)
  private static final int EXPECTED = 5;

  private static Analyzer keyword() {
    return new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
  }

  private int count(Query q) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(keyword()))) {
        for (String v : VALUES) {
          Document d = new Document();
          d.add(new StringField("f", v, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return new IndexSearcher(r).count(q);
      }
    }
  }

  public void testRegexpQueryUnescapedControl() throws Exception {
    assertEquals(EXPECTED, count(new RegexpQuery(new Term("f", "[.-z]"))));
  }

  public void testRegexpQueryEscapedStart() throws Exception {
    assertEquals(EXPECTED, count(new RegexpQuery(new Term("f", "[\\.-z]"))));
  }

  public void testClassicQueryParserEscapedStart() throws Exception {
    Query q = new QueryParser("f", keyword()).parse("/[\\.-z]/");
    System.out.println("classic QP parsed: " + q + " (" + q.getClass().getSimpleName() + ")");
    assertEquals(EXPECTED, count(q));
  }
}
