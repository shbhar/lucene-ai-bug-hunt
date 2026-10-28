package verifyv3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.intervals.IntervalIterator;
import org.apache.lucene.queries.intervals.IntervalMatchesIterator;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Intended-behaviour probe (public API only). Each line prints: label, doc text, count of the doc,
 * Weight#matches non-null, and the first interval's [start,end] gaps/width from intervals() and
 * from matches().
 */
public class IntendedProbe {
  static final String[] DOCS = {
    "new york new york", // 0
    "new york x new york", // 1
    "new york new jersey", // 2 control: distinct sub-sources
    "a x a b", // 3
    "a x c b", // 4 control: distinct inner sub-sources
    "a a", // 5 single-term repeat: unaffected by the sign
  };

  static IndexSearcher searcher;
  static DirectoryReader reader;

  public static void main(String[] args) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    Analyzer an = new StandardAnalyzer();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(an))) {
      for (int i = 0; i < DOCS.length; i++) {
        Document d = new Document();
        d.add(new TextField("f", DOCS[i], Field.Store.NO));
        d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        w.addDocument(d);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    IntervalsSource ny = Intervals.phrase("new", "york");
    IntervalsSource nj = Intervals.phrase("new", "jersey");
    IntervalsSource a = Intervals.term("a"), b = Intervals.term("b"), c = Intervals.term("c");

    probe("P1 maxgaps0 ordered(ny,ny)", Intervals.maxgaps(0, Intervals.ordered(ny, ny)), 0);
    probe("P1c maxgaps0 ordered(ny,nj) [control]", Intervals.maxgaps(0, Intervals.ordered(ny, nj)), 2);
    probe("P2 maxgaps1 ordered(ny,ny)", Intervals.maxgaps(1, Intervals.ordered(ny, ny)), 1);
    probe("P3 maxgaps0 unordered(ny,ny)", Intervals.maxgaps(0, Intervals.unordered(ny, ny)), 0);
    probe("P3c maxgaps0 unordered(ny,nj) [control]", Intervals.maxgaps(0, Intervals.unordered(ny, nj)), 2);
    probe("P4 raw ordered(ny,ny)", Intervals.ordered(ny, ny), 0);
    probe("P4c raw ordered(ny,nj) [control]", Intervals.ordered(ny, nj), 2);
    probe("P5 maxgaps0 ordered(a,a) single-term", Intervals.maxgaps(0, Intervals.ordered(a, a)), 5);
    // W2 ("span" manifestation): nested repeated ordered vs nested distinct ordered (cf. #13819)
    probe("W2 maxgaps0 ordered(ordered(a,a),b)", Intervals.maxgaps(0, Intervals.ordered(Intervals.ordered(a, a), b)), 3);
    probe("W2c maxgaps0 ordered(ordered(a,c),b) [control]", Intervals.maxgaps(0, Intervals.ordered(Intervals.ordered(a, c), b)), 4);

    // Query-parser reachability (flexible StandardQueryParser interval functions)
    StandardQueryParser qp = new StandardQueryParser(an);
    for (String qs :
        new String[] {
          "fn:maxgaps(0 fn:ordered(\"new york\" \"new york\"))",
          "fn:maxgaps(0 fn:ordered(\"new york\" \"new jersey\"))",
          "fn:maxgaps(0 fn:unordered(\"new york\" \"new york\"))"
        }) {
      Query q = qp.parse(qs, "f");
      System.out.println("QP " + qs + " -> " + q + " | hits on doc0/doc2: " + countDoc(q, 0) + "/" + countDoc(q, 2));
    }
    reader.close();
  }

  static int countDoc(Query q, int id) throws Exception {
    Query qq =
        new BooleanQuery.Builder()
            .add(q, BooleanClause.Occur.MUST)
            .add(new TermQuery(new Term("id", Integer.toString(id))), BooleanClause.Occur.FILTER)
            .build();
    return searcher.count(qq);
  }

  static void probe(String label, IntervalsSource src, int id) throws Exception {
    Query q = new IntervalQuery("f", src);
    int cnt = countDoc(q, id);
    LeafReaderContext ctx = reader.leaves().get(0);
    Weight wt = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
    boolean m = wt.matches(ctx, id) != null;
    String iv = "-";
    IntervalIterator it = src.intervals("f", ctx);
    if (it != null && it.advance(id) == id && it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
      iv = "[" + it.start() + "," + it.end() + " g" + it.gaps() + " w" + it.width() + "]";
    }
    String mv = "-";
    IntervalMatchesIterator mi = src.matches("f", ctx, id);
    if (mi != null && mi.next()) {
      mv = "[" + mi.startPosition() + "," + mi.endPosition() + " g" + mi.gaps() + " w" + mi.width() + "]";
    }
    System.out.println(
        label + " | doc" + id + " '" + DOCS[id] + "' | count=" + cnt + " matchesNonNull=" + m
            + " | intervals " + iv + " | matches " + mv);
  }
}
