import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterDirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Independent verification (verify, candidate 4): AutomatonTermsEnum on an NFA whose lazily
 * determinized form reaches a non-accepting state with no outgoing transitions.
 *
 * <p>Everything is deterministic: fixed docs, codec pinned to TestUtil.getDefaultCodec(), plain
 * IndexSearcher (no newSearcher wrapping), plain ByteBuffersDirectory.
 */
public class TestVerifyNfaDeadState extends LuceneTestCase {

  static final String[] TERMS = {"a", "aa", "b", "bc", "bcd", "c"};
  static final String F = "f";

  /** NFA: 0 -a-> 1 (dead: non-accept, no transitions), 0 -b-> 2 (accept), 0 -b-> 3 -c-> 2. */
  static Automaton nfaWithDeadState() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int dead = a.createState();
    int acc = a.createState();
    int s3 = a.createState();
    a.setAccept(acc, true);
    a.addTransition(s0, dead, 'a');
    a.addTransition(s0, acc, 'b');
    a.addTransition(s0, s3, 'b');
    a.addTransition(s3, acc, 'c');
    a.finishState();
    return a;
  }

  /** Same language {b, bc}, NFA, but no dead state. */
  static Automaton nfaWithoutDeadState() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int acc = a.createState();
    int s2 = a.createState();
    a.setAccept(acc, true);
    a.addTransition(s0, acc, 'b');
    a.addTransition(s0, s2, 'b');
    a.addTransition(s2, acc, 'c');
    a.finishState();
    return a;
  }

  /** DFA for {b, bc} plus a reachable dead state on 'a' (documented as NOT allowed for DFAs). */
  static Automaton dfaWithDeadState() {
    Automaton a = new Automaton();
    int s0 = a.createState();
    int dead = a.createState();
    int b = a.createState();
    int bc = a.createState();
    a.setAccept(b, true);
    a.setAccept(bc, true);
    a.addTransition(s0, dead, 'a');
    a.addTransition(s0, b, 'b');
    a.addTransition(b, bc, 'c');
    a.finishState();
    return a;
  }

  Directory dir;
  DirectoryReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String t : TERMS) {
        Document d = new Document();
        d.add(new StringField(F, t, Field.Store.NO));
        d.add(new SortedSetDocValuesField(F, new BytesRef(t)));
        w.addDocument(d);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  static String describe(Throwable t) {
    StackTraceElement[] st = t.getStackTrace();
    StringBuilder sb = new StringBuilder(t.toString());
    for (int i = 0; i < Math.min(4, st.length); i++) sb.append("\n      at ").append(st[i]);
    return sb.toString();
  }

  static List<String> drain(TermsEnum te) throws IOException {
    List<String> out = new ArrayList<>();
    for (BytesRef br = te.next(); br != null; br = te.next()) out.add(br.utf8ToString());
    return out;
  }

  // ---------- preconditions about the input automaton ----------

  public void testPreconditions() {
    Automaton a = nfaWithDeadState();
    assertFalse("must be an NFA", a.isDeterministic());
    assertTrue(Operations.hasDeadStatesFromInitial(a));
    assertFalse(Operations.isEmpty(a));
    // Operations.run asserts a DFA, so check membership on the determinized copy
    Automaton det = Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    assertTrue(Operations.run(det, "b"));
    assertTrue(Operations.run(det, "bc"));
    assertFalse(Operations.run(det, "a"));
    assertFalse(Operations.run(det, "bcd"));
    assertFalse(nfaWithoutDeadState().isDeterministic());
    assertFalse(Operations.hasDeadStatesFromInitial(nfaWithoutDeadState()));
    CompiledAutomaton ca = new CompiledAutomaton(a);
    assertEquals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL, ca.type);
    assertNull("NFA path => no ByteRunAutomaton", ca.runAutomaton);
    System.out.println(
        "[pre] codec postings=" + TestUtil.getDefaultCodec().postingsFormat().getName());
  }

  // ---------- inverted index, block-tree IntersectTermsEnum (reference behaviour) ----------

  public void testInvertedIndexBlockTreeIntersectIsCorrect() throws IOException {
    IndexSearcher s = new IndexSearcher(reader);
    int n = s.count(new AutomatonQuery(new Term(F, ""), nfaWithDeadState()));
    System.out.println("[blocktree] count=" + n);
    assertEquals(2, n);
    Terms terms = reader.leaves().get(0).reader().terms(F);
    System.out.println("[blocktree] terms class=" + terms.getClass().getName());
    List<String> got = drain(new CompiledAutomaton(nfaWithDeadState()).getTermsEnum(terms));
    System.out.println("[blocktree] intersect terms=" + got);
    assertEquals(List.of("b", "bc"), got);
  }

  // ---------- AutomatonTermsEnum paths ----------

  public void testDocValuesRewriteNfaWithDeadState() throws IOException {
    IndexSearcher s = new IndexSearcher(reader);
    AutomatonQuery q =
        new AutomatonQuery(
            new Term(F, ""), nfaWithDeadState(), false, MultiTermQuery.DOC_VALUES_REWRITE);
    try {
      int n = s.count(q);
      System.out.println("[dv-rewrite dead NFA] count=" + n);
      assertEquals(2, n);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("[dv-rewrite dead NFA] THREW " + describe(e));
      throw e;
    }
  }

  public void testSortedSetDocValuesIntersectNfaWithDeadState() throws IOException {
    SortedSetDocValues dv = reader.leaves().get(0).reader().getSortedSetDocValues(F);
    try {
      List<String> got = drain(dv.intersect(new CompiledAutomaton(nfaWithDeadState())));
      System.out.println("[SSDV.intersect dead NFA] terms=" + got);
      assertEquals(List.of("b", "bc"), got);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("[SSDV.intersect dead NFA] THREW " + describe(e));
      throw e;
    }
  }

  /** Inverted index, but read through a FilterLeafReader whose FilterTerms doesn't override intersect. */
  public void testFilterTermsWrapperNfaWithDeadState() throws IOException {
    DirectoryReader wrapped =
        new FilterDirectoryReader(
            reader,
            new FilterDirectoryReader.SubReaderWrapper() {
              @Override
              public LeafReader wrap(LeafReader r) {
                return new FilterLeafReader(r) {
                  @Override
                  public Terms terms(String field) {
                    Terms t = super.terms(field);
                    return t == null ? null : new FilterTerms(t) {};
                  }

                  @Override
                  public CacheHelper getCoreCacheHelper() {
                    return null;
                  }

                  @Override
                  public CacheHelper getReaderCacheHelper() {
                    return null;
                  }
                };
              }
            }) {
          @Override
          protected DirectoryReader doWrapDirectoryReader(DirectoryReader in) {
            throw new UnsupportedOperationException();
          }

          @Override
          public CacheHelper getReaderCacheHelper() {
            return null;
          }
        };
    IndexSearcher s = new IndexSearcher(wrapped);
    try {
      int n = s.count(new AutomatonQuery(new Term(F, ""), nfaWithDeadState()));
      System.out.println("[FilterTerms wrapper dead NFA] count=" + n);
      assertEquals(2, n);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("[FilterTerms wrapper dead NFA] THREW " + describe(e));
      throw e;
    }
  }

  public void testMemoryIndexNfaWithDeadState() {
    Analyzer an = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    MemoryIndex mi = new MemoryIndex();
    mi.addField(F, "a aa b bc bcd c", an);
    try {
      float score = mi.search(new AutomatonQuery(new Term(F, ""), nfaWithDeadState()));
      System.out.println("[MemoryIndex dead NFA] score=" + score);
      assertTrue(score > 0);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("[MemoryIndex dead NFA] THREW " + describe(e));
      throw e;
    }
  }

  /** Public RegexpQuery API reaching the bug only via a user-supplied AutomatonProvider. */
  public void testRegexpQueryWithProviderDocValuesRewrite() throws IOException {
    IndexSearcher s = new IndexSearcher(reader);
    RegexpQuery q =
        new RegexpQuery(
            new Term(F, "<x>"),
            RegExp.ALL,
            0,
            name -> name.equals("x") ? nfaWithDeadState() : null,
            MultiTermQuery.DOC_VALUES_REWRITE);
    try {
      int n = s.count(q);
      System.out.println("[RegexpQuery <x> provider dv-rewrite] count=" + n);
      assertEquals(2, n);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("[RegexpQuery <x> provider dv-rewrite] THREW " + describe(e));
      throw e;
    }
  }

  // ---------- controls (expected to pass on main) ----------

  public void testControlsOnAutomatonTermsEnumPaths() throws IOException {
    IndexSearcher s = new IndexSearcher(reader);
    Analyzer an = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    Automaton[] controls = {
      Operations.removeDeadStates(nfaWithDeadState()),
      nfaWithoutDeadState(),
      new RegExp("a#|b|bc").toAutomaton(), // RegExp union/concat strip dead states
      new RegExp("b(c)?").toAutomaton(),
    };
    String[] names = {"removeDeadStates(nfa)", "nfaWithoutDeadState", "RegExp a#|b|bc", "RegExp b(c)?"};
    for (int i = 0; i < controls.length; i++) {
      Automaton c = controls[i];
      int dv =
          s.count(new AutomatonQuery(new Term(F, ""), c, false, MultiTermQuery.DOC_VALUES_REWRITE));
      MemoryIndex mi = new MemoryIndex();
      mi.addField(F, "a aa b bc bcd c", an);
      float score = mi.search(new AutomatonQuery(new Term(F, ""), c));
      System.out.println(
          "[control "
              + names[i]
              + "] det="
              + c.isDeterministic()
              + " deadFromInitial="
              + Operations.hasDeadStatesFromInitial(c)
              + " dvCount="
              + dv
              + " memScore="
              + score);
      assertEquals(names[i], 2, dv);
      assertTrue(names[i], score > 0);
    }
  }

  /** Informational variants: dead branch after all matches ('z'), and an all-dead (empty-language) NFA. */
  public void testInfoVariants() throws IOException {
    IndexSearcher s = new IndexSearcher(reader);
    Automaton z = new Automaton();
    int z0 = z.createState(), zDead = z.createState(), zAcc = z.createState(), z3 = z.createState();
    z.setAccept(zAcc, true);
    z.addTransition(z0, zAcc, 'b');
    z.addTransition(z0, z3, 'b');
    z.addTransition(z0, zDead, 'z');
    z.addTransition(z3, zAcc, 'c');
    z.finishState();
    Automaton e = new Automaton(); // empty language, NFA, every reachable state dead
    int e0 = e.createState(), e1 = e.createState(), e2 = e.createState();
    e.addTransition(e0, e1, 'b');
    e.addTransition(e0, e2, 'b');
    e.finishState();
    Automaton[] as = {z, e};
    String[] names = {"dead-on-z {b,bc}", "empty-language NFA"};
    int[] expected = {2, 0};
    for (int i = 0; i < as.length; i++) {
      String bt, dv;
      try {
        bt = "count=" + s.count(new AutomatonQuery(new Term(F, ""), as[i]));
      } catch (Throwable t) {
        bt = "THREW " + t;
      }
      try {
        dv =
            "count="
                + s.count(
                    new AutomatonQuery(
                        new Term(F, ""), as[i], false, MultiTermQuery.DOC_VALUES_REWRITE));
      } catch (Throwable t) {
        dv = "THREW " + t;
      }
      System.out.println(
          "[info variant " + names[i] + "] det=" + as[i].isDeterministic() + " expected="
              + expected[i] + " blocktree " + bt + " | dv-rewrite " + dv);
    }
  }

  /** Informational: a DFA with a dead state (explicitly disallowed by CompiledAutomaton javadoc). */
  public void testInfoDfaWithDeadState() throws IOException {
    Automaton d = dfaWithDeadState();
    assertTrue(d.isDeterministic());
    IndexSearcher s = new IndexSearcher(reader);
    String r;
    try {
      r =
          "count="
              + s.count(
                  new AutomatonQuery(new Term(F, ""), d, false, MultiTermQuery.DOC_VALUES_REWRITE));
    } catch (Throwable t) {
      r = "THREW " + describe(t);
    }
    System.out.println("[info DFA-with-dead-state dv-rewrite] " + r);
    try {
      r = "count=" + s.count(new AutomatonQuery(new Term(F, ""), d));
    } catch (Throwable t) {
      r = "THREW " + describe(t);
    }
    System.out.println("[info DFA-with-dead-state blocktree] " + r);
  }
}
