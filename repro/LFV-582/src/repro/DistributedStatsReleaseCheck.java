package repro;

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Release-API variant (Lucene 8.3 .. 10.x: termStatistics(Term, int, long) and
 * collectionStatistics(String)) of TestDistributedStatsLocallyAbsentTerm. Exits 1 if the shard
 * score of doc A differs from its score in the whole collection for the MultiPhraseQuery
 * "(quick|fast) fox" (body:fast exists only on the other shard); the TermQuery control must match.
 */
public class DistributedStatsReleaseCheck {

  static final class DistributedStatsSearcher extends IndexSearcher {
    final IndexSearcher other;

    DistributedStatsSearcher(IndexReader local, IndexSearcher other) {
      super(local);
      this.other = other;
    }

    @Override
    public TermStatistics termStatistics(Term term, int docFreq, long totalTermFreq)
        throws IOException {
      IndexReader o = other.getIndexReader();
      return new TermStatistics(
          term.bytes(), docFreq + o.docFreq(term), totalTermFreq + o.totalTermFreq(term));
    }

    @Override
    public CollectionStatistics collectionStatistics(String field) throws IOException {
      CollectionStatistics a = super.collectionStatistics(field);
      CollectionStatistics b = other.collectionStatistics(field);
      if (a == null) return b;
      if (b == null) return a;
      return new CollectionStatistics(
          field,
          a.maxDoc() + b.maxDoc(),
          a.docCount() + b.docCount(),
          a.sumTotalTermFreq() + b.sumTotalTermFreq(),
          a.sumDocFreq() + b.sumDocFreq());
    }
  }

  static Directory index(String[] bodies) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setSimilarity(new BM25Similarity());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String b : bodies) {
        Document d = new Document();
        d.add(new TextField("body", b, Field.Store.NO));
        w.addDocument(d);
      }
    }
    return dir;
  }

  public static void main(String[] args) throws Exception {
    Directory d0 = index(new String[] {"quick fox"});
    Directory d1 = index(new String[] {"fast dog", "fast cat", "fast fast bird"});
    IndexReader r0 = DirectoryReader.open(d0);
    IndexReader r1 = DirectoryReader.open(d1);
    IndexReader all = new MultiReader(new IndexReader[] {r0, r1}, false);
    IndexSearcher other = new IndexSearcher(r1);
    IndexSearcher shard = new DistributedStatsSearcher(r0, other);
    IndexSearcher global = new IndexSearcher(all);
    for (IndexSearcher s : new IndexSearcher[] {other, shard, global}) {
      s.setSimilarity(new BM25Similarity());
    }
    Query control = new TermQuery(new Term("body", "quick"));
    MultiPhraseQuery.Builder b = new MultiPhraseQuery.Builder();
    b.add(new Term[] {new Term("body", "quick"), new Term("body", "fast")});
    b.add(new Term("body", "fox"));
    Query mpq = b.build();
    boolean controlOk = true, ok = true;
    for (Query q : new Query[] {control, mpq}) {
      float sv = shard.explain(q, 0).getValue().floatValue();
      float gv = global.explain(q, 0).getValue().floatValue();
      float ss = shard.search(q, 1).scoreDocs[0].score;
      float gs = global.search(q, 1).scoreDocs[0].score;
      boolean eq = Float.compare(sv, gv) == 0 && Float.compare(ss, gs) == 0;
      System.out.println(
          q + ": shard score=" + ss + " (explain " + sv + "), whole collection score=" + gs
              + " (explain " + gv + ") -> " + (eq ? "EQUAL" : "DIFFERENT"));
      if (q == control) controlOk = eq;
      else ok = eq;
    }
    all.close();
    r0.close();
    r1.close();
    if (!controlOk) {
      System.out.println("SETUP ERROR: control differs");
      System.exit(2);
    }
    System.exit(ok ? 0 : 1);
  }
}
