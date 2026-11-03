// Fixture generator: run on the OLD release (for example 10.2.2) with -Dtests.seed pinned,
// because the old QueryDecomposer orders disjuncts by HashSet, whose order follows the per-JVM
// hash seed (StringHelper.GOOD_FAST_HASH_SEED, taken from tests.seed when it is set).
// Usage: java -Dtests.seed=<SEED> WriteMonitorIndex <indexDir>
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.monitor.Monitor;
import org.apache.lucene.monitor.MonitorConfiguration;
import org.apache.lucene.monitor.MonitorQuery;
import org.apache.lucene.monitor.MonitorQuerySerializer;
import org.apache.lucene.monitor.QueryDecomposer;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.Version;

public class WriteMonitorIndex {
  static final String QUERY = "alpha beta";

  static Query parse(String s) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : s.split(" ")) {
      b.add(new TermQuery(new Term("f", t)), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  public static void main(String[] args) throws Exception {
    Path path = Paths.get(args[0]);
    MonitorConfiguration config =
        new MonitorConfiguration()
            .setIndexPath(path, MonitorQuerySerializer.fromParser(WriteMonitorIndex::parse));
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), config)) {
      monitor.register(new MonitorQuery("q", parse(QUERY), QUERY, Collections.emptyMap()));
    }
    int i = 0;
    for (Query part : new QueryDecomposer().decompose(parse(QUERY))) {
      System.out.println(Version.LATEST + " tests.seed=" + System.getProperty("tests.seed") + " cacheId q_" + (i++) + " -> " + part);
    }
  }
}
