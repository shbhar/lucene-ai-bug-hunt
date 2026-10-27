package sa1verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.ByteRunnable;

/**
 * Independent verification of the finding: the query produced by MultiTermQuery.DOC_VALUES_REWRITE
 * does not report its wrapped MultiTermQuery to a QueryVisitor. Uses only public API (package is not
 * org.apache.lucene.search). Each "bug" test asserts the CORRECT behaviour and is expected to FAIL on
 * an unpatched main; each "control" test is expected to PASS.
 */
public class TestVerifySA1 extends LuceneTestCase {

  /** Records every leaf callback a visitor receives. */
  static final class LeafRecorder extends QueryVisitor {
    final List<String> leaves = new ArrayList<>();

    @Override
    public void consumeTerms(Query query, Term... terms) {
      leaves.add("consumeTerms:" + query);
    }

    @Override
    public void consumeTermsMatching(
        Query query, String field, Supplier<ByteRunnable> automaton) {
      leaves.add("consumeTermsMatching:" + query);
    }

    @Override
    public void visitLeaf(Query query) {
      leaves.add("visitLeaf:" + query);
    }
  }

  private static List<String> leaves(Query q) {
    LeafRecorder r = new LeafRecorder();
    q.visit(r);
    return r.leaves;
  }

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document d = new Document();
      d.add(new StringField("f", "abc", Field.Store.NO));
      d.add(new SortedSetDocValuesField("f", new BytesRef("abc")));
      d.add(new SortedDocValuesField("s", new BytesRef("abc")));
      w.addDocument(d);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader); // plain searcher: no asserting wrappers
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static Query dvPrefix(String p) {
    return new PrefixQuery(new Term("f", p), MultiTermQuery.DOC_VALUES_REWRITE);
  }

  private static Query blendedPrefix(String p) {
    return new PrefixQuery(new Term("f", p)); // default CONSTANT_SCORE_BLENDED_REWRITE
  }

  // ---------------- controls (expected to pass) ----------------

  public void testControlOriginalQueriesReportOneLeaf() {
    assertEquals(1, leaves(dvPrefix("ab")).size());
    assertEquals(1, leaves(blendedPrefix("ab")).size());
    assertEquals(
        1, leaves(SortedSetDocValuesField.newSlowSetQuery("f", List.of(new BytesRef("abc")))).size());
  }

  public void testControlBlendedRewriteReportsLeaf() throws IOException {
    Query rewritten = searcher.rewrite(blendedPrefix("ab"));
    System.out.println("blended rewritten=" + rewritten.getClass().getName() + " leaves=" + leaves(rewritten));
    assertEquals(1, leaves(rewritten).size());
  }

  public void testControlSearchResultsAreCorrect() throws IOException {
    assertEquals(1, searcher.count(dvPrefix("ab")));
    assertEquals(0, searcher.count(dvPrefix("x")));
    assertEquals(1, searcher.count(SortedSetDocValuesField.newSlowSetQuery("f", List.of(new BytesRef("abc")))));
  }

  public void testControlNestedClauseLimitBlended() throws IOException {
    int old = IndexSearcher.getMaxClauseCount();
    IndexSearcher.setMaxClauseCount(8);
    try {
      Query q = twoMustOfFiveShould(false);
      expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.rewrite(q));
    } finally {
      IndexSearcher.setMaxClauseCount(old);
    }
  }

  // ---------------- bug assertions (expected to FAIL on unpatched main) ----------------

  public void testBugDocValuesRewrittenPrefixReportsLeaf() throws IOException {
    Query rewritten = searcher.rewrite(dvPrefix("ab"));
    List<String> l = leaves(rewritten);
    System.out.println("dv rewritten=" + rewritten + " class=" + rewritten.getClass().getName() + " leaves=" + l);
    assertEquals("leaves reported for rewritten DV prefix query", 1, l.size());
  }

  public void testBugSlowSetQueryRewrittenReportsLeaf() throws IOException {
    Query original = SortedSetDocValuesField.newSlowSetQuery("f", List.of(new BytesRef("abc")));
    Query rewritten = searcher.rewrite(original);
    System.out.println("SSDV newSlowSetQuery original leaves=" + leaves(original) + " rewritten leaves=" + leaves(rewritten));
    assertEquals(leaves(original).size(), leaves(rewritten).size());
  }

  public void testBugSortedSlowSetQueryRewrittenReportsLeaf() throws IOException {
    Query original = SortedDocValuesField.newSlowSetQuery("s", List.of(new BytesRef("abc")));
    Query rewritten = searcher.rewrite(original);
    System.out.println("SDV newSlowSetQuery original leaves=" + leaves(original) + " rewritten leaves=" + leaves(rewritten));
    assertEquals(leaves(original).size(), leaves(rewritten).size());
  }

  public void testBugNestedClauseLimitDocValuesRewrite() throws IOException {
    int old = IndexSearcher.getMaxClauseCount();
    IndexSearcher.setMaxClauseCount(8);
    try {
      Query q = twoMustOfFiveShould(true);
      // 10 leaf MTQs > maxClauseCount=8: should throw like the blended control does
      expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.rewrite(q));
    } finally {
      IndexSearcher.setMaxClauseCount(old);
    }
  }

  /** outer: MUST(inner1) MUST(inner2); each inner = 5 SHOULD prefix queries with distinct prefixes. */
  private static Query twoMustOfFiveShould(boolean docValuesRewrite) {
    BooleanQuery.Builder outer = new BooleanQuery.Builder();
    for (int i = 0; i < 2; i++) {
      BooleanQuery.Builder inner = new BooleanQuery.Builder();
      for (int j = 0; j < 5; j++) {
        String p = "p" + i + "_" + j;
        inner.add(docValuesRewrite ? dvPrefix(p) : blendedPrefix(p), Occur.SHOULD);
      }
      outer.add(inner.build(), Occur.MUST);
    }
    return outer.build();
  }
}
