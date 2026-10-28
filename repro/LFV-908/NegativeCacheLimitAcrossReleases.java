import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CachingCollector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.grouping.GroupingSearch;
import org.apache.lucene.search.grouping.TopGroups;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/** Version-agnostic (8.0+) check: a negative cache limit must not make the second pass replay nothing. */
public class NegativeCacheLimitAcrossReleases {
  static final class Counting extends SimpleCollector {
    int count;
    @Override public void collect(int doc) { count++; }
    @Override public ScoreMode scoreMode() { return ScoreMode.COMPLETE_NO_SCORES; }
  }

  @SuppressWarnings("deprecation")
  public static void main(String[] args) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 20; i++) {
        Document d = new Document();
        d.add(new StringField("body", "match", Field.Store.NO));
        d.add(new SortedDocValuesField("author", new BytesRef("author" + (i % 3))));
        w.addDocument(d);
      }
    }
    int bad = 0;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Query q = new TermQuery(new Term("body", "match"));
      for (int mode = 0; mode < 2; mode++) {
        GroupingSearch gs = new GroupingSearch("author");
        if (mode == 0) gs.setCaching(-1, true); else gs.setCachingInMB(-1.0, true);
        TopGroups<?> tg = gs.search(s, q, 0, 10);
        boolean ok = tg.totalHitCount == 20;
        if (!ok) bad++;
        System.out.println("NEG " + (mode == 0 ? "setCaching(-1,true)" : "setCachingInMB(-1.0,true)")
            + " totalHitCount=" + tg.totalHitCount + " (expected 20) -> " + (ok ? "ok" : "WRONG"));
      }
      Counting first = new Counting();
      CachingCollector cc = CachingCollector.create(first, false, -1);
      s.search(q, cc);
      Counting second = new Counting();
      if (cc.isCached()) cc.replay(second);
      boolean ok = !cc.isCached() || second.count == first.count;
      if (!ok) bad++;
      System.out.println("NEG CachingCollector.create(c,false,-1) collected=" + first.count + " isCached=" + cc.isCached()
          + " replayed=" + second.count + " -> " + (ok ? "ok" : "WRONG"));
    }
    System.exit(bad == 0 ? 0 : 1);
  }
}
