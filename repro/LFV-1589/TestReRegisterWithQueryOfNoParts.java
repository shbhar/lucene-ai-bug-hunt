import static org.junit.Assert.assertEquals;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.monitor.Monitor;
import org.apache.lucene.monitor.MonitorQuery;
import org.apache.lucene.monitor.QueryMatch;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;

public class TestReRegisterWithQueryOfNoParts {

  @Test
  public void testReplacementByPureNegativeQueryTakesEffect() throws Exception {
    Document doc = new Document();
    doc.add(new TextField("f", "apple", Field.Store.NO));
    Query notPear =
        new BooleanQuery.Builder().add(new TermQuery(new Term("f", "pear")), Occur.MUST_NOT).build();
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer())) {
      monitor.register(new MonitorQuery("q1", new TermQuery(new Term("f", "apple"))));
      monitor.register(new MonitorQuery("q1", notPear));
      // expected 0: q1 is now -f:pear, which matches no document; actual 1: f:apple is still reported
      assertEquals(0, monitor.match(doc, QueryMatch.SIMPLE_MATCHER).getMatchCount());
    }
  }
}
