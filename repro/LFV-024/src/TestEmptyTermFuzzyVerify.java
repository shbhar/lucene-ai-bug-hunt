import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.FuzzyTermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.ByteRunAutomaton;

/**
 * Independent verification of the finding: FuzzyQuery on the empty term matches only the
 * empty term, although the constructor javadoc promises "terms with an edit distance of at most
 * maxEdits to term" and LUCENE-7439 (6.3.0) says "FuzzyQuery now matches all terms within the
 * specified edit distance, even if they are short terms".
 *
 * <p>Oracle: a brute-force optimal-string-alignment (Damerau-Levenshtein, FuzzyQuery's default
 * since transpositions=true) distance over code points, written here, independent of Lucene.
 */
public class TestEmptyTermFuzzyVerify extends LuceneTestCase {

  static final String F = "f";
  // Distances from "": 0, 1, 1, 2, 3, 1. Distances from "a": 1, 0, 1, 1, 2, 1.
  static final String[] TERMS = {"", "a", "b", "ab", "abc", "x"};

  /** Brute-force OSA distance over code points (the oracle). */
  static int osa(String s, String t) {
    int[] a = s.codePoints().toArray();
    int[] b = t.codePoints().toArray();
    int[][] d = new int[a.length + 1][b.length + 1];
    for (int i = 0; i <= a.length; i++) d[i][0] = i;
    for (int j = 0; j <= b.length; j++) d[0][j] = j;
    for (int i = 1; i <= a.length; i++) {
      for (int j = 1; j <= b.length; j++) {
        int cost = a[i - 1] == b[j - 1] ? 0 : 1;
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
      }
    }
    return d[a.length][b.length];
  }

  static TreeSet<String> oracle(String q, int maxEdits) {
    TreeSet<String> out = new TreeSet<>();
    for (String t : TERMS) {
      if (osa(q, t) <= maxEdits) out.add(t);
    }
    return out;
  }

  static ByteBuffersDirectory buildIndex() throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    // untokenized field WITH positions so that the same field serves FuzzyQuery and IntervalQuery
    FieldType ft = new FieldType(StringField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
    ft.freeze();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String t : TERMS) {
        Document doc = new Document();
        doc.add(new org.apache.lucene.document.Field(F, t, ft));
        doc.add(new StoredField("v", t));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  static TreeSet<String> hits(Query q) throws Exception {
    try (ByteBuffersDirectory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      TreeSet<String> got = new TreeSet<>();
      for (ScoreDoc sd : s.search(q, 100).scoreDocs) {
        got.add(r.storedFields().document(sd.doc).get("v"));
      }
      return got;
    }
  }

  static String show(java.util.Collection<String> c) {
    List<String> l = new ArrayList<>();
    for (String s : c) l.add("\"" + s + "\"");
    return l.toString();
  }

  static void report(String label, TreeSet<String> expected, TreeSet<String> got) {
    System.out.println(
        ""
            + label
            + " expected(oracle)="
            + show(expected)
            + " observed="
            + show(got)
            + (expected.equals(got) ? " OK" : " MISMATCH"));
  }

  /** Sanity: my oracle agrees with hand-computed distances. */
  public void testOracleSanity() {
    assertEquals(0, osa("", ""));
    assertEquals(1, osa("", "a"));
    assertEquals(2, osa("", "ab"));
    assertEquals(3, osa("", "abc"));
    assertEquals(1, osa("ab", "ba"));
    assertEquals(1, osa("a", ""));
  }

  /** The automaton layer is correct for the empty term: it accepts exactly the oracle set. */
  public void testAutomatonLayerAcceptsShortTermsForEmptyQuery() {
    for (int n = 0; n <= 2; n++) {
      ByteRunAutomaton ra = FuzzyQuery.getFuzzyAutomaton("", n, 0, true).runAutomaton;
      TreeSet<String> accepted = new TreeSet<>();
      for (String t : TERMS) {
        BytesRef b = new BytesRef(t);
        if (ra.run(b.bytes, b.offset, b.length)) accepted.add(t);
      }
      report("getFuzzyAutomaton(\"\"," + n + ") accepts", oracle("", n), accepted);
      assertEquals(show(oracle("", n)), show(accepted));
    }
  }

  /** Control: a non-empty 1-char query term matches everything within maxEdits (LUCENE-7439). */
  public void testControlOneCharTerm() throws Exception {
    for (int n = 1; n <= 2; n++) {
      TreeSet<String> got = hits(new FuzzyQuery(new Term(F, "a"), n));
      report("FuzzyQuery(\"a\"," + n + ")", oracle("a", n), got);
      assertEquals(show(oracle("a", n)), show(got));
    }
  }

  /** The candidate bug, default rewrite (TopTermsBlendedFreqScoringRewrite(50)). */
  public void testEmptyTermDefaultRewrite() throws Exception {
    List<String> failures = new ArrayList<>();
    for (int n = 1; n <= 2; n++) {
      TreeSet<String> got = hits(new FuzzyQuery(new Term(F, ""), n));
      report("FuzzyQuery(\"\"," + n + ")", oracle("", n), got);
      if (!oracle("", n).equals(got)) failures.add("maxEdits=" + n + " got " + show(got));
    }
    assertTrue("FuzzyQuery on empty term misses terms within maxEdits: " + failures, failures.isEmpty());
  }

  /** Same with a non-top-terms rewrite: shows the cause is in FuzzyTermsEnum, not in scoring. */
  public void testEmptyTermConstantScoreRewrite() throws Exception {
    Query q =
        new FuzzyQuery(new Term(F, ""), 2, 0, 50, true, MultiTermQuery.CONSTANT_SCORE_REWRITE);
    TreeSet<String> got = hits(q);
    report("FuzzyQuery(\"\",2,CONSTANT_SCORE_REWRITE)", oracle("", 2), got);
    assertEquals(show(oracle("", 2)), show(got));
  }

  /** Symmetry: d("a","") == d("","a") == 1, but only one direction matches. */
  public void testSymmetry() throws Exception {
    boolean aFindsEmpty = hits(new FuzzyQuery(new Term(F, "a"), 1)).contains("");
    boolean emptyFindsA = hits(new FuzzyQuery(new Term(F, ""), 1)).contains("a");
    System.out.println(
        "symmetry: FuzzyQuery(\"a\",1) matches \"\"="
            + aFindsEmpty
            + "  FuzzyQuery(\"\",1) matches \"a\"="
            + emptyFindsA);
    assertTrue(aFindsEmpty);
    assertEquals("edit distance is symmetric", aFindsEmpty, emptyFindsA);
  }

  /** Differential vs another Lucene API documented with the same semantics. */
  public void testIntervalsFuzzyTermDisagrees() throws Exception {
    TreeSet<String> intervals = hits(new IntervalQuery(F, Intervals.fuzzyTerm("", 2)));
    TreeSet<String> fuzzy = hits(new FuzzyQuery(new Term(F, ""), 2));
    report("IntervalQuery(Intervals.fuzzyTerm(\"\",2))", oracle("", 2), intervals);
    assertEquals(show(oracle("", 2)), show(intervals));
    assertEquals("FuzzyQuery vs Intervals.fuzzyTerm for the same term/maxEdits", show(intervals), show(fuzzy));
  }

  /** Direct look at FuzzyTermsEnum: which terms it yields, and its internal maxEdits after ctor. */
  public void testFuzzyTermsEnumDirect() throws Exception {
    try (ByteBuffersDirectory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      LeafReader leaf = r.leaves().get(0).reader();
      Terms terms = leaf.terms(F);
      Field maxEditsField = FuzzyTermsEnum.class.getDeclaredField("maxEdits");
      maxEditsField.setAccessible(true);
      for (String q : new String[] {"", "a"}) {
        FuzzyTermsEnum te = new FuzzyTermsEnum(terms, new Term(F, q), 2, 0, true);
        int effective = maxEditsField.getInt(te);
        TreeSet<String> got = new TreeSet<>();
        for (BytesRef b = te.next(); b != null; b = te.next()) got.add(b.utf8ToString());
        System.out.println(
            "new FuzzyTermsEnum(term=\""
                + q
                + "\", maxEdits=2): effective maxEdits after ctor="
                + effective
                + " (1f - 2f/termLength = "
                + (1.0f - 2.0f / (float) q.codePointCount(0, q.length()))
                + ")");
        report("FuzzyTermsEnum(\"" + q + "\",2) terms", oracle(q, 2), got);
      }
      FuzzyTermsEnum te = new FuzzyTermsEnum(terms, new Term(F, ""), 2, 0, true);
      assertEquals("effective maxEdits for empty term", 2, maxEditsField.getInt(te));
    }
  }
  /**
   * FuzzyQuery.visit (used by highlighters / Matches-style term extraction) reports an automaton
   * that accepts "a" and "ab" for the same query that does not match them.
   */
  public void testVisitorAutomatonDisagreesWithMatches() throws Exception {
    FuzzyQuery q = new FuzzyQuery(new Term(F, ""), 2);
    List<org.apache.lucene.util.automaton.ByteRunnable> ras = new ArrayList<>();
    q.visit(
        new QueryVisitor() {
          @Override
          public void consumeTermsMatching(
              Query query, String field, java.util.function.Supplier<org.apache.lucene.util.automaton.ByteRunnable> automaton) {
            ras.add(automaton.get());
          }
        });
    assertEquals(1, ras.size());
    TreeSet<String> visitorAccepts = new TreeSet<>();
    for (String t : TERMS) {
      BytesRef b = new BytesRef(t);
      if (ras.get(0).run(b.bytes, b.offset, b.length)) visitorAccepts.add(t);
    }
    TreeSet<String> matched = hits(q);
    report("FuzzyQuery(\"\",2).visit automaton accepts", oracle("", 2), visitorAccepts);
    assertEquals(show(oracle("", 2)), show(visitorAccepts));
    assertEquals("visit() automaton vs actual matches", show(visitorAccepts), show(matched));
  }
}
