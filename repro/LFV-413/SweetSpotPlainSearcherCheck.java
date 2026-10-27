import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.misc.SweetSpotSimilarity;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Plain-API check (no test framework): default IndexWriterConfig, default codec, plain
 * IndexSearcher. Compares search(query, 10) (TOP_SCORES after 1,000 hits) against a complete
 * evaluation. Exits 1 if any configuration with an expected mismatch differs, as a bug signal.
 */
public class SweetSpotPlainSearcherCheck {

  static String words(String head, int total) {
    StringBuilder sb = new StringBuilder(head);
    int have = head.isEmpty() ? 0 : head.split(" ").length;
    for (int i = have; i < total; i++) sb.append(sb.length() == 0 ? "" : " ").append("a");
    return sb.toString();
  }

  /** returns true if TOP_SCORES top hit equals complete top hit */
  static boolean run(String label, Similarity sim, int shortLen, int goodLen) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer()).setSimilarity(sim);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // 2000 docs: "t" once in a field of goodLen terms
        for (int i = 0; i < 2000; i++) {
          Document d = new Document();
          d.add(new TextField("f", words("t", goodLen), Field.Store.NO));
          w.addDocument(d);
        }
        // 1000 docs: "t" twice in a short field (shortLen terms); one doc has "t" twice in goodLen
        for (int i = 0; i < 1000; i++) {
          Document d = new Document();
          d.add(new TextField("f", i == 150 ? words("t t", goodLen) : words("t t", shortLen), Field.Store.NO));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setSimilarity(sim);
        Query q = new TermQuery(new Term("f", "t"));
        TopDocs complete = s.search(q, new TopScoreDocCollectorManager(10, null, Integer.MAX_VALUE));
        TopDocs top = s.search(q, 10);
        boolean same = complete.scoreDocs[0].doc == top.scoreDocs[0].doc;
        System.out.println(label + ": complete top=" + complete.scoreDocs[0].doc + " (" + complete.scoreDocs[0].score
            + "), search(q,10) top=" + top.scoreDocs[0].doc + " (" + top.scoreDocs[0].score + "), totalHits=" + top.totalHits
            + " -> " + (same ? "SAME" : "DIFFERENT"));
        return same;
      }
    }
  }

  public static void main(String[] args) throws Exception {
    boolean bug = false;
    SweetSpotSimilarity a = new SweetSpotSimilarity();
    a.setLengthNormFactors(10, 20, 0.5f);
    bug |= !run("SweetSpot(10,20,0.5) short=2 good=10", a, 2, 10);
    SweetSpotSimilarity b = new SweetSpotSimilarity();
    b.setLengthNormFactors(3, 5, 0.5f); // plateau values of a widely published configuration example
    bug |= !run("SweetSpot(3,5,0.5)   short=1 good=3 ", b, 1, 3);
    // controls: monotone configurations must agree
    SweetSpotSimilarity c = new SweetSpotSimilarity();
    c.setLengthNormFactors(1, 20, 0.5f);
    boolean ctl1 = run("control SweetSpot(1,20,0.5) short=2 good=10", c, 2, 10);
    boolean ctl2 = run("control ClassicSimilarity short=2 good=10", new ClassicSimilarity(), 2, 10);
    System.out.println("controls agree: " + (ctl1 && ctl2));
    if (!(ctl1 && ctl2)) System.exit(2);
    System.exit(bug ? 1 : 0);
  }
}
