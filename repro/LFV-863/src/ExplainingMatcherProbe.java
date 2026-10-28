import java.util.*;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.monitor.*;
import org.apache.lucene.queries.spans.*;
import org.apache.lucene.search.*;

/** Monitor with ExplainingMatch.MATCHER explains every matching registered query; a query that nests a span query in a filter makes the whole match call fail. */
public class ExplainingMatcherProbe {
  public static void main(String[] a) throws Exception {
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer())) {
      Query spans = new SpanNearQuery(new SpanQuery[] {new SpanTermQuery(new Term("body", "brown")), new SpanTermQuery(new Term("body", "fox"))}, 0, true);
      Query filter = new BooleanQuery.Builder().add(spans, BooleanClause.Occur.SHOULD).add(new TermQuery(new Term("body", "dog")), BooleanClause.Occur.SHOULD).build();
      Query q1 = new BooleanQuery.Builder().add(new TermQuery(new Term("body", "quick")), BooleanClause.Occur.MUST).add(filter, BooleanClause.Occur.FILTER).build();
      monitor.register(new MonitorQuery("q1", q1), new MonitorQuery("q2", new TermQuery(new Term("body", "quick"))));
      Document doc = new Document();
      doc.add(new TextField("body", "quick brown fox", Field.Store.NO));
      MatchingQueries<QueryMatch> simple = monitor.match(doc, QueryMatch.SIMPLE_MATCHER);
      System.out.println("SIMPLE_MATCHER matches: " + simple.getMatchCount() + " errors: " + simple.getErrors().keySet());
      try {
        MatchingQueries<ExplainingMatch> ex = monitor.match(doc, ExplainingMatch.MATCHER);
        System.out.println("ExplainingMatch.MATCHER matches: " + ex.getMatchCount() + " errors: " + ex.getErrors());
        if (ex.getMatchCount() != simple.getMatchCount() || !ex.getErrors().isEmpty()) { System.out.println("FAIL: explaining matcher disagrees with simple matcher"); System.exit(1); }
        System.out.println("PASS");
      } catch (Throwable t) {
        System.out.println("FAIL: monitor.match(doc, ExplainingMatch.MATCHER) threw " + t + " at " + t.getStackTrace()[0]);
        System.exit(1);
      }
    }
  }
}
