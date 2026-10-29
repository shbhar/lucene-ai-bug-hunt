import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

/**
 * Release-history probe for Lucene 5.x-8.x (spans still in org.apache.lucene.search.spans).
 * Exit 1 if two wrappers that match different documents compare equal, or if two deletes in one
 * flush window collapse into one. Exit 0 if both behave.
 */
public class SpanWrapperEqualsOldApi {
  static SpanMultiTermQueryWrapper<PrefixQuery> all() {
    return new SpanMultiTermQueryWrapper<PrefixQuery>(new PrefixQuery(new Term("f", "aa")));
  }

  static SpanMultiTermQueryWrapper<PrefixQuery> top() {
    SpanMultiTermQueryWrapper<PrefixQuery> q = all();
    q.setRewriteMethod(new SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite(1));
    return q;
  }

  public static void main(String[] args) throws Exception {
    String[] values = {"aa1", "aa2", "aa2", "aa3", "aa3", "aa3"};
    int violations = 0;
    Directory dir = new RAMDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()));
    for (String v : values) {
      Document d = new Document();
      d.add(new TextField("f", v, Field.Store.NO));
      w.addDocument(d);
    }
    w.commit();
    DirectoryReader r = DirectoryReader.open(dir);
    IndexSearcher s = new IndexSearcher(r);
    int ca = s.count(all()), ct = s.count(top());
    boolean eq = all().equals(top());
    System.out.println("PROBE count(all)=" + ca + " count(top1)=" + ct + " equals=" + eq);
    if (eq && ca != ct) violations++;
    r.close();
    w.deleteDocuments(top());
    w.deleteDocuments(all());
    w.commit();
    r = DirectoryReader.open(dir);
    System.out.println("PROBE delete(top1); delete(all); numDocs=" + r.numDocs() + " expected=0");
    if (r.numDocs() != 0) violations++;
    r.close();
    w.close();
    dir.close();
    System.out.println("PROBE violations=" + violations);
    System.exit(violations == 0 ? 0 : 1);
  }
}
