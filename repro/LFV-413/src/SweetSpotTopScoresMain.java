// Version-portable main() variant of TestSweetSpotTopScores#testTopHitsMatchCompleteEvaluation (Lucene 7.x-9.x
// APIs: TopScoreDocCollector.create(numHits, totalHitsThreshold) instead of TopScoreDocCollectorManager).
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.misc.SweetSpotSimilarity;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

public class SweetSpotTopScoresMain {
  public static void main(String[] args) throws Exception {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setLengthNormFactors(10, 20, 0.5f, true); // 4-arg form (discountOverlaps) before 10.0
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer())
        .setSimilarity(sim).setMergePolicy(NoMergePolicy.INSTANCE).setMaxBufferedDocs(100_000))) {
      for (int i = 0; i < 2000; i++) {
        Document doc = new Document();
        doc.add(new TextField("f", "t a a a a a a a a a", Field.Store.NO));
        w.addDocument(doc);
      }
      for (int i = 0; i < 1000; i++) {
        Document doc = new Document();
        doc.add(new TextField("f", i == 150 ? "t t a a a a a a a a" : "t t", Field.Store.NO));
        w.addDocument(doc);
      }
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(sim);
      TermQuery q = new TermQuery(new Term("f", "t"));
      TopScoreDocCollector completeCollector = TopScoreDocCollector.create(10, Integer.MAX_VALUE);
      searcher.search(q, completeCollector);
      TopDocs complete = completeCollector.topDocs();
      TopDocs top = searcher.search(q, 10);
      System.out.println("complete: doc=" + complete.scoreDocs[0].doc + " score=" + complete.scoreDocs[0].score
          + " | search(q,10): doc=" + top.scoreDocs[0].doc + " score=" + top.scoreDocs[0].score);
      if (complete.scoreDocs[0].doc != top.scoreDocs[0].doc) {
        System.out.println("FAIL: the default top-k search misses the best hit");
        System.exit(1);
      }
      System.out.println("PASS");
    }
  }
}
