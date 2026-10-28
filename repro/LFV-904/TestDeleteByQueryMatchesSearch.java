package org.apache.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.queries.function.FunctionRangeQuery;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * IndexWriter.deleteDocuments(Query...) says it "Deletes the document(s) matching any of the
 * provided queries". Each test indexes the same documents into a fixed segment layout, records
 * which documents IndexSearcher.search(query) returns on that index, then deletes with the same
 * query and checks that exactly those documents were deleted. Public API only.
 */
public class TestDeleteByQueryMatchesSearch extends LuceneTestCase {

  private static final Analyzer WS =
      new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
          return new TokenStreamComponents(new MockTokenizer(MockTokenizer.WHITESPACE, false));
        }
      };

  private static IndexWriterConfig noMergeConfig() {
    IndexWriterConfig iwc = new IndexWriterConfig(WS);
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    return iwc;
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  static Set<String> searchIds(IndexSearcher s, Query q) throws IOException {
    TopDocs td = s.search(q, 10_000);
    StoredFields sf = s.storedFields();
    Set<String> out = new TreeSet<>();
    for (ScoreDoc sd : td.scoreDocs) {
      out.add(sf.document(sd.doc).get("id"));
    }
    return out;
  }

  private static Set<String> liveIds(IndexReader r) throws IOException {
    Set<String> out = new TreeSet<>();
    for (LeafReaderContext ctx : r.leaves()) {
      Bits live = ctx.reader().getLiveDocs();
      StoredFields sf = ctx.reader().storedFields();
      for (int d = 0; d < ctx.reader().maxDoc(); d++) {
        if (live == null || live.get(d)) {
          out.add(sf.document(d).get("id"));
        }
      }
    }
    return out;
  }

  /** The oracle: what IndexSearcher.search(q) returns over the whole index. */
  protected Set<String> expectedIds(DirectoryReader r, Query q) throws IOException {
    return searchIds(new IndexSearcher(r), q);
  }

  /**
   * Indexes {@code segments} (one commit per inner list), then compares search(q) with the set
   * that deleteDocuments(q) (or updateDocuments(q, [replacement]) when {@code viaUpdate}) actually
   * removes.
   */
  private void assertDeleteMatchesSearch(
      String what, IndexWriterConfig iwc, List<List<Document>> segments, Query q, boolean viaUpdate)
      throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (List<Document> seg : segments) {
        for (Document d : seg) {
          w.addDocument(d);
        }
        w.commit();
      }
      Set<String> expected;
      Set<String> before;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("segment layout", segments.size(), r.leaves().size());
        expected = expectedIds(r, q);
        before = liveIds(r);
      }
      if (viaUpdate) {
        w.updateDocuments(q, List.of(doc("replacement")));
      } else {
        w.deleteDocuments(q);
      }
      w.commit();
      Set<String> deleted = new TreeSet<>(before);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        deleted.removeAll(liveIds(r));
      }
      assertEquals(
          what
              + ": the documents deleted by "
              + (viaUpdate ? "updateDocuments" : "deleteDocuments")
              + "("
              + q
              + ") must be the documents IndexSearcher.search returns on the same index",
          expected,
          deleted);
    }
  }

  // ---------------------------------------------------------------- per-document control

  public void testTermQueryControl() throws IOException {
    List<List<Document>> segs = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      List<Document> seg = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        Document d = doc("s" + s + "-" + i);
        d.add(new StringField("t", (i % 2 == 0) ? "x" : "y", Field.Store.NO));
        seg.add(d);
      }
      segs.add(seg);
    }
    assertDeleteMatchesSearch(
        "TermQuery control", noMergeConfig(), segs, new TermQuery(new Term("t", "x")), false);
  }

  // ---------------------------------------------------------------- kNN

  private static List<List<Document>> knnSegments(int numSegments) {
    // Segment s holds vectors (100*s + i, 0) for i = 0..4; the query vector is (0, 0), so the
    // two nearest documents of the whole index are s0-0 and s0-1.
    List<List<Document>> segs = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      List<Document> seg = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        Document d = doc("s" + s + "-" + i);
        d.add(
            new KnnFloatVectorField(
                "v", new float[] {100f * s + i, 0f}, VectorSimilarityFunction.EUCLIDEAN));
        seg.add(d);
      }
      segs.add(seg);
    }
    if (numSegments == 1) {
      List<Document> all = new ArrayList<>();
      segs.forEach(all::addAll);
      return List.of(all);
    }
    return segs;
  }

  public void testKnnSingleSegmentControl() throws IOException {
    assertDeleteMatchesSearch(
        "kNN k=2, one segment",
        noMergeConfig(),
        knnSegments(1),
        new KnnFloatVectorQuery("v", new float[] {0f, 0f}, 2),
        false);
  }

  public void testKnnTwoSegments() throws IOException {
    assertDeleteMatchesSearch(
        "kNN k=2, two segments",
        noMergeConfig(),
        knnSegments(2),
        new KnnFloatVectorQuery("v", new float[] {0f, 0f}, 2),
        false);
  }

  public void testKnnTwoSegmentsDefaultConfig() throws IOException {
    // Default IndexWriterConfig (TieredMergePolicy), only the codec is pinned.
    IndexWriterConfig iwc = new IndexWriterConfig(WS);
    iwc.setCodec(TestUtil.getDefaultCodec());
    assertDeleteMatchesSearch(
        "kNN k=2, two segments, default merge policy",
        iwc,
        knnSegments(2),
        new KnnFloatVectorQuery("v", new float[] {0f, 0f}, 2),
        false);
  }

  public void testKnnUpdateDocuments() throws IOException {
    assertDeleteMatchesSearch(
        "kNN k=2, two segments, updateDocuments(Query, docs)",
        noMergeConfig(),
        knnSegments(2),
        new KnnFloatVectorQuery("v", new float[] {0f, 0f}, 2),
        true);
  }

  // ---------------------------------------------------------------- top-N term rewrites

  public void testTopTermsRewriteOfPrefixQuery() throws IOException {
    // segment 0 has terms pa, pb; segment 1 has pc, pd. All prefix matches have the same boost,
    // so the top-1 term over the whole index is "pa" (ties go to the smaller term).
    String[][] terms = {{"pa", "pb"}, {"pc", "pd"}};
    List<List<Document>> segs = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      List<Document> seg = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        Document d = doc("s" + s + "-" + i);
        d.add(new StringField("t", terms[s][i], Field.Store.NO));
        seg.add(d);
      }
      segs.add(seg);
    }
    Query q =
        new PrefixQuery(
            new Term("t", "p"), new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1));
    assertDeleteMatchesSearch("PrefixQuery with top-1 terms rewrite", noMergeConfig(), segs, q, false);
  }

  public void testDefaultFuzzyQuery() throws IOException {
    // 60 distinct terms at edit distance 1 from "aaaaaa" (one substitution with b..k at one of six
    // positions). The default FuzzyQuery keeps the top 50 terms (maxExpansions = 50).
    List<String> variants = new ArrayList<>();
    for (int p = 0; p < 6; p++) {
      for (char c = 'b'; c <= 'k'; c++) {
        char[] chars = "aaaaaa".toCharArray();
        chars[p] = c;
        variants.add(new String(chars));
      }
    }
    variants.sort(null);
    assertEquals(60, variants.size());
    List<List<Document>> segs = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      List<Document> seg = new ArrayList<>();
      for (int i = 0; i < 30; i++) {
        String term = variants.get(s * 30 + i);
        Document d = doc(term);
        d.add(new StringField("f", term, Field.Store.NO));
        seg.add(d);
      }
      segs.add(seg);
    }
    assertDeleteMatchesSearch(
        "default FuzzyQuery (maxExpansions=50)",
        noMergeConfig(),
        segs,
        new FuzzyQuery(new Term("f", "aaaaaa")),
        false);
  }

  // ---------------------------------------------------------------- index statistics

  public void testCommonTermsQuery() throws IOException {
    // Whole index: maxDoc 10, "common" in 6 docs > ceil(0.5*10) -> high frequency; "rare" in 1 doc
    // -> low frequency, so only the "rare" document matches. Segment 1 on its own: "common" in
    // 1 of 5 docs -> low frequency there.
    List<List<Document>> segs = new ArrayList<>();
    List<Document> seg0 = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Document d = doc("s0-" + i);
      d.add(new StringField("t", "common", Field.Store.NO));
      if (i == 0) {
        d.add(new StringField("t", "rare", Field.Store.NO));
      }
      seg0.add(d);
    }
    List<Document> seg1 = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Document d = doc("s1-" + i);
      d.add(new StringField("t", i == 0 ? "common" : "other", Field.Store.NO));
      seg1.add(d);
    }
    segs.add(seg0);
    segs.add(seg1);
    CommonTermsQuery q = new CommonTermsQuery(Occur.SHOULD, Occur.SHOULD, 0.5f);
    q.add(new Term("t", "common"));
    q.add(new Term("t", "rare"));
    assertDeleteMatchesSearch("CommonTermsQuery", noMergeConfig(), segs, q, false);
  }

  public void testScoreRangePredicate() throws IOException {
    // Every document has a one-token field "s"; "common" is in 3 of 3 docs of segment 0 and in 1
    // of 3 docs of segment 1. Over the whole index all four "common" documents get the same BM25
    // score; the range is that score +-1%.
    List<List<Document>> segs = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      List<Document> seg = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        Document d = doc("s" + s + "-" + i);
        boolean common = s == 0 || i == 0;
        d.add(new TextField("s", common ? "common" : "other", Field.Store.NO));
        seg.add(d);
      }
      segs.add(seg);
    }
    // Compute the whole-index score on an identical index first.
    float score;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, noMergeConfig())) {
      for (List<Document> seg : segs) {
        for (Document d : seg) {
          w.addDocument(d);
        }
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        TopDocs td = new IndexSearcher(r).search(new TermQuery(new Term("s", "common")), 10);
        assertEquals(4, td.scoreDocs.length);
        score = td.scoreDocs[0].score;
        for (ScoreDoc sd : td.scoreDocs) {
          assertEquals(score, sd.score, 0f);
        }
      }
    }
    Query q =
        new FunctionRangeQuery(
            new QueryValueSource(new TermQuery(new Term("s", "common")), 0f),
            score * 0.99f,
            score * 1.01f,
            true,
            true);
    assertDeleteMatchesSearch("score-range FunctionRangeQuery", noMergeConfig(), segs, q, false);
  }
}
