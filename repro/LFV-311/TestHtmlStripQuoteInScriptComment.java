package htmlstripverify;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: a quote character inside a {@code <!-- ... -->} section of a script or style
 * element makes HTMLStripCharFilter drop the text that follows the closing end tag.
 *
 * <p>Public API only (HTMLStripCharFilter(Reader), Reader.read, Analyzer.initReader override).
 * The control tests (no quote, or no comment wrapper) pass; the others assert that the text after
 * the closed script/style element survives.
 */
public class TestHtmlStripQuoteInScriptComment extends LuceneTestCase {

  private static String strip(String html) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (Reader r = new HTMLStripCharFilter(new StringReader(html))) {
      char[] buf = new char[7];
      int n;
      while ((n = r.read(buf, 0, buf.length)) != -1) {
        sb.append(buf, 0, n);
      }
    }
    return sb.toString();
  }

  private static void assertKeeps(String html, String... words) throws IOException {
    String out = strip(html);
    for (String w : words) {
      assertTrue(
          "expected '" + w + "' in stripped output; input=[" + html + "] output=[" + out + "]",
          out.contains(w));
    }
  }

  // ---------------------------------------------------------------- controls (expected to pass)

  public void testControlScriptCommentWithoutQuote() throws IOException {
    assertKeeps(
        "<p>before</p><script><!--\n// keep this\nvar a = 1;\n//--></script><p>after</p>",
        "before",
        "after");
  }

  public void testControlStyleCommentWithoutQuote() throws IOException {
    assertKeeps(
        "<p>before</p><style><!--\n/* margin fix */\np { margin: 0 }\n--></style><p>after</p>",
        "before",
        "after");
  }

  public void testControlApostropheInScriptWithoutCommentWrapper() throws IOException {
    assertKeeps("<p>before</p><script>\n// it's fine\nvar a = 1;\n</script><p>after</p>", "after");
  }

  public void testControlBalancedQuotesInScriptComment() throws IOException {
    assertKeeps(
        "<p>before</p><script><!--\nvar s = 'it is';\n//--></script><p>after</p>", "after");
  }

  // ---------------------------------------------------------------- claimed failures

  public void testApostropheInScriptLineComment() throws IOException {
    assertKeeps(
        "<p>before</p><script><!--\n// it's fine\nvar a = 1;\n//--></script><p>after</p>",
        "before",
        "after");
  }

  public void testApostropheInStyleBlockComment() throws IOException {
    assertKeeps(
        "<p>before</p><style><!--\n/* the client's fix */\np { margin: 0 }\n--></style><p>after</p>",
        "before",
        "after");
  }

  public void testDoubleQuoteInScriptLineComment() throws IOException {
    assertKeeps(
        "<p>before</p><script><!--\n// a 6\" wide box\nvar a = 1;\n//--></script><p>after</p>",
        "after");
  }

  public void testSecondApostropheInBodyDoesNotRecover() throws IOException {
    assertKeeps(
        "<script><!--\n// it's fine\n//--></script><p>Tom's book</p><p>after</p>", "after");
  }

  public void testUnterminatedHtmlCommentOpenerInScript() throws IOException {
    assertKeeps("<p>before</p><script>a<!--</script><p>after</p>", "after");
  }

  /** End-user effect: the words after the script block are not searchable. */
  public void testIndexedFieldMissesTextAfterScript() throws IOException {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t);
          }

          @Override
          protected Reader initReader(String fieldName, Reader reader) {
            return new HTMLStripCharFilter(reader);
          }
        };
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document control = new Document();
      control.add(new StringField("id", "control", Field.Store.NO));
      control.add(
          new TextField(
              "body",
              "<script><!--\n// keep this\n//--></script> shipped today",
              Field.Store.NO));
      w.addDocument(control);
      Document quote = new Document();
      quote.add(new StringField("id", "quote", Field.Store.NO));
      quote.add(
          new TextField(
              "body", "<script><!--\n// it's fine\n//--></script> shipped today", Field.Store.NO));
      w.addDocument(quote);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = newSearcher(r);
        for (String id : new String[] {"control", "quote"}) {
          BooleanQuery q =
              new BooleanQuery.Builder()
                  .add(new TermQuery(new Term("id", id)), BooleanClause.Occur.FILTER)
                  .add(new TermQuery(new Term("body", "shipped")), BooleanClause.Occur.MUST)
                  .build();
          assertEquals("doc '" + id + "' should match body:shipped", 1, s.count(q));
        }
      }
    } finally {
      analyzer.close();
    }
  }
}
