import java.util.ListIterator;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorImpl;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorPipeline;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.processors.PointQueryNodeProcessor;

/** Insert a processor right after PointQueryNodeProcessor with ListIterator.add (args: control = use add(int, p)). */
public class PipelineInsertAfterMain {
  static final class Recorder extends QueryNodeProcessorImpl {
    boolean sawHandler;
    @Override
    public QueryNode process(QueryNode tree) throws QueryNodeException {
      sawHandler = getQueryConfigHandler() != null;
      return tree;
    }
    @Override protected QueryNode preProcessNode(QueryNode n) { return n; }
    @Override protected QueryNode postProcessNode(QueryNode n) { return n; }
    @Override protected java.util.List<QueryNode> setChildrenOrder(java.util.List<QueryNode> c) { return c; }
  }

  public static void main(String[] args) throws Exception {
    boolean control = args.length > 0 && args[0].equals("control");
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    QueryNodeProcessorPipeline pipe = (QueryNodeProcessorPipeline) qp.getQueryNodeProcessor();
    Recorder r = new Recorder();
    if (control) {
      int k = 0;
      while (!(pipe.get(k) instanceof PointQueryNodeProcessor)) k++;
      pipe.add(k + 1, r);
    } else {
      ListIterator<org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessor> it = pipe.listIterator();
      while (it.hasNext()) {
        if (it.next() instanceof PointQueryNodeProcessor) { it.add(r); break; }
      }
    }
    qp.parse("foo", "f");
    System.out.println((control ? "control add(int,p)" : "ListIterator.add") + ": parser handler non-null="
        + (qp.getQueryConfigHandler() != null) + ", inserted processor saw handler=" + r.sawHandler);
    if (!r.sawHandler) throw new AssertionError("processor inserted through the pipeline's List API has no QueryConfigHandler");
  }
}
