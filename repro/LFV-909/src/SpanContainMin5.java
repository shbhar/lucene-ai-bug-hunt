import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.spans.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;

/** Minimal: little = spanOr(spanNear([c, d], slop 1, ordered), a) has spans [0,3) and [1,2) in "c a d";
 *  [1,2) (the 'a') lies inside big = a [1,2), so SpanWithinQuery(big, little) must match; likewise
 *  SpanContainingQuery(spanNear([a,b],0,true), spanOr(spanNear([a,a],5,true), b)) on "a b a" (big [0,2) contains b [1,2)). */
public class SpanContainMin5 {
  public static void main(String[] x) throws Exception {
    Directory dir = new RAMDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()))) {
      for (String s : new String[]{"c a d", "a b a", "a"}) { Document d = new Document(); d.add(new TextField("f", s, Field.Store.YES)); w.addDocument(d); }
    }
    IndexSearcher s = new IndexSearcher(DirectoryReader.open(dir));
    SpanQuery a = new SpanTermQuery(new Term("f", "a")), b = new SpanTermQuery(new Term("f", "b")),
        c = new SpanTermQuery(new Term("f", "c")), d = new SpanTermQuery(new Term("f", "d"));
    SpanQuery little1 = new SpanOrQuery(new SpanNearQuery(new SpanQuery[]{c, d}, 1, true), a);
    SpanQuery big2 = new SpanNearQuery(new SpanQuery[]{a, b}, 0, true);
    SpanQuery little2 = new SpanOrQuery(new SpanNearQuery(new SpanQuery[]{a, a}, 5, true), b);
    Query[] qs = {
      new SpanWithinQuery(a, little1),            // expect docs 0 ("c a d"), 1 and 2
      new SpanContainingQuery(a, little1),        // expect docs 0, 1 and 2
      new SpanContainingQuery(big2, little2),     // expect doc 1 ("a b a")
      new SpanWithinQuery(big2, little2),         // expect doc 1
      new SpanWithinQuery(a, a)                   // control: docs 0, 1, 2
    };
    String[] expect = {"[0, 1, 2]", "[0, 1, 2]", "[1]", "[1]", "[0, 1, 2]"};
    int bad = 0;
    for (int i = 0; i < qs.length; i++) {
      java.util.List<Integer> hits = new java.util.ArrayList<>();
      for (ScoreDoc sd : s.search(qs[i], 10).scoreDocs) hits.add(sd.doc);
      java.util.Collections.sort(hits);
      boolean ok = hits.toString().equals(expect[i]); if (!ok) bad++;
      System.out.println((ok ? "ok   " : "FAIL ") + qs[i] + " expected " + expect[i] + " got " + hits + " count=" + s.count(qs[i]));
    }
    System.out.println("lucene=" + org.apache.lucene.util.Version.LATEST + " failures=" + bad);
    System.exit(bad == 0 ? 0 : 1);
  }
}
