package org.apache.lucene.queries.spans;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Probes for the intended-behaviour question. Each probe prints what it sees and asserts the
 * documented semantics (negative post only allows MORE overlap, so it can never drop a span that
 * post = 0 keeps; adding an exclude span far away can never add include spans).
 */
public class TestSpanNotNegPostV3Probes extends LuceneTestCase {

  static SpanTermQuery t(String s) {
    return new SpanTermQuery(new Term("f", s));
  }

  static List<String> spans(IndexSearcher searcher, SpanQuery q, int doc) throws Exception {
    List<String> out = new ArrayList<>();
    SpanWeight w =
        ((SpanQuery) searcher.rewrite(q)).createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
    Spans spans = w.getSpans(searcher.getIndexReader().leaves().get(0), SpanWeight.Postings.POSITIONS);
    if (spans == null || spans.advance(doc) != doc) return out;
    while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
      out.add("[" + spans.startPosition() + "," + spans.endPosition() + ")");
    }
    return out;
  }

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()))) {
      for (String text : new String[] {"b a a c", "b a a c b", "b x a a c", "b x a a c b x"}) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  // doc 1 = doc 0 plus an extra exclude 'b' AFTER both 'a's; that extra exclude restores the dropped span
  public void testFarExcludeAddsIncludeSpans() throws Exception {
    SpanQuery q = new SpanNotQuery(t("a"), t("b"), 0, -1);
    List<String> d0 = spans(searcher, q, 0);
    List<String> d1 = spans(searcher, q, 1);
    System.out.println("PROBE far-exclude: 'b a a c' -> " + d0 + " ; 'b a a c b' -> " + d1);
    assertEquals("adding an exclude span can only remove include spans", d1, d0);
  }

  // the 3-arg constructor (dist) is also affected
  public void testDistConstructor() throws Exception {
    List<String> p0 = spans(searcher, new SpanNotQuery(t("a"), t("b"), 0), 0);
    List<String> pm1 = spans(searcher, new SpanNotQuery(t("a"), t("b"), -1), 0);
    System.out.println("PROBE dist: dist=0 -> " + p0 + " ; dist=-1 -> " + pm1);
    assertEquals(p0, pm1);
  }

  // two-phase exclude (SpanNear) path is also affected
  public void testTwoPhaseExclude() throws Exception {
    SpanQuery ex = new SpanNearQuery(new SpanQuery[] {t("b"), t("x")}, 0, true);
    List<String> p0 = spans(searcher, new SpanNotQuery(t("a"), ex, 0, 0), 2);
    List<String> pm1 = spans(searcher, new SpanNotQuery(t("a"), ex, 0, -1), 2);
    System.out.println("PROBE two-phase exclude 'b x a a c': post=0 -> " + p0 + " ; post=-1 -> " + pm1);
    assertEquals(p0, pm1);
  }

  // top-level: the document still matches (first include is accepted when exclude exhausts), but
  // the span frequency, hence the score, drops
  public void testTopLevelScore() throws Exception {
    SpanQuery q0 = new SpanNotQuery(t("a"), t("b"), 0, 0);
    SpanQuery qm1 = new SpanNotQuery(t("a"), t("b"), 0, -1);
    TopDocs td0 = searcher.search(q0, 10);
    TopDocs tdm1 = searcher.search(qm1, 10);
    System.out.println("PROBE top-level: post=0 hits=" + td0.totalHits + " ; post=-1 hits=" + tdm1.totalHits);
    float s0 = Float.NaN, sm1 = Float.NaN;
    for (var sd : td0.scoreDocs) if (sd.doc == 0) s0 = sd.score;
    for (var sd : tdm1.scoreDocs) if (sd.doc == 0) sm1 = sd.score;
    System.out.println("PROBE top-level score doc0 'b a a c': post=0 " + s0 + " ; post=-1 " + sm1);
    assertEquals(td0.totalHits.value(), tdm1.totalHits.value());
    assertEquals("score of doc 0 should not depend on post when no overlap exists", s0, sm1, 0f);
  }
}
