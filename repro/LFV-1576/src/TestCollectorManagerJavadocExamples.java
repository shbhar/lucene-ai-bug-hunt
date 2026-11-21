import java.net.URI;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCollectorManagerJavadocExamples extends LuceneTestCase {

  // Expected: the three class-javadoc "Example usage" blocks compile; actual: "MatchAllDocsQuery() has private access" in each.
  public void testCompile() throws Exception {
    String source =
        """
        import org.apache.lucene.misc.search.*;
        import org.apache.lucene.sandbox.search.*;
        import org.apache.lucene.search.*;
        import org.apache.lucene.search.grouping.*;
        import org.apache.lucene.search.grouping.AllGroupHeadsCollectorManager.GroupHeadsResult;
        import org.apache.lucene.util.*;
        class Example {
          void allGroupHeadsCollectorManager(IndexSearcher searcher) throws Exception {
            AllGroupHeadsCollectorManager<BytesRef> manager =
                new AllGroupHeadsCollectorManager<>(
                    () -> new TermGroupSelector("category"), Sort.RELEVANCE);
            GroupHeadsResult result = searcher.search(new MatchAllDocsQuery(), manager);
            Bits groupHeadsBits = result.retrieveGroupHeads(searcher.getIndexReader().maxDoc());
          }
          void docValuesStatsCollectorManager(IndexSearcher searcher) throws Exception {
            DocValuesStatsCollectorManager<Long, DocValuesStats.LongDocValuesStats> manager =
                new DocValuesStatsCollectorManager<>(() -> new DocValuesStats.LongDocValuesStats("price"));
            DocValuesStats.LongDocValuesStats stats = searcher.search(new MatchAllDocsQuery(), manager);
          }
          void largeNumHitsTopDocsCollectorManager(IndexSearcher searcher) throws Exception {
            TopDocs topDocs = searcher.search(new MatchAllDocsQuery(),
                new LargeNumHitsTopDocsCollectorManager(100_000));
            System.out.println("Total hits: " + topDocs.totalHits.value());
          }
        }
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
