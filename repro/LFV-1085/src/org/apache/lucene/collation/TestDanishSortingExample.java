package org.apache.lucene.collation;

import java.net.URI;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDanishSortingExample extends LuceneTestCase {

  // Expected: the package-info.java "Danish Sorting" example (copied verbatim) compiles; actual: "cannot find symbol: method setSort(SortField)".
  public void testCompiles() throws Exception {
    String source =
        """
        import static org.junit.Assert.assertEquals;
        import java.nio.file.*;
        import java.text.Collator;
        import java.util.Locale;
        import org.apache.lucene.analysis.Analyzer;
        import org.apache.lucene.collation.*;
        import org.apache.lucene.document.*;
        import org.apache.lucene.index.*;
        import org.apache.lucene.search.*;
        import org.apache.lucene.store.*;
        class Example { void run() throws Exception {
          Collator collator = Collator.getInstance(new Locale("da", "dk"));
          Analyzer analyzer = new CollationKeyAnalyzer(collator);
          Path dirPath = Files.createTempDirectory("tempIndex");
          Directory dir = FSDirectory.open(dirPath);
          IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
          String[] tracer = new String[] { "A", "B", "C", "D", "E" };
          String[] data = new String[] { "HAT", "HUT", "H\\u00C5T", "H\\u00D8T", "HOT" };
          String[] sortedTracerOrder = new String[] { "A", "E", "B", "D", "C" };
          CollationDocValuesField dvField = new CollationDocValuesField("contents", collator);
          for (int i = 0 ; i < data.length ; ++i) {
            Document doc = new Document();
            doc.add(new StoredField("tracer", tracer[i]));
            doc.add(new TextField("contents", data[i], Field.Store.NO));
            dvField.setStringValue(data[i]);
            doc.add(dvField);
            writer.addDocument(doc);
          }
          writer.close();
          IndexReader ir = DirectoryReader.open(dir);
          IndexSearcher searcher = new IndexSearcher(ir);
          Sort sort = new Sort();
          sort.setSort(new SortField("contents", SortField.Type.STRING));
          Query query = MatchAllDocsQuery.INSTANCE;
          ScoreDoc[] result = searcher.search(query, 1000, sort).scoreDocs;
          for (int i = 0 ; i < result.length ; ++i) {
            Document doc = searcher.storedFields().document(result[i].doc);
            assertEquals(sortedTracerOrder[i], doc.getValues("tracer")[0]);
          }
        } }
        """;
    JavaFileObject file =
        new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
          }
        };
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
    List<String> options =
        List.of("-proc:none", "-d", createTempDir().toString(), "-cp", System.getProperty("java.class.path"));
    boolean compiled = javac.getTask(null, null, errors, options, null, List.of(file)).call();
    assertTrue(errors.getDiagnostics().toString(), compiled);
  }
}
