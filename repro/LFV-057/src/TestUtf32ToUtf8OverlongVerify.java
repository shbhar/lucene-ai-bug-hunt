import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.FiniteStringsIterator;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.Transition;
import org.apache.lucene.util.automaton.UTF32ToUTF8;

/**
 * Independent verification of: "UTF32ToUTF8 still accepts overlong (invalid) UTF-8 for
 * ranges spanning into 3-/4-byte code points".
 *
 * <p>Oracle used here is self-contained: enc() is the RFC 3629 bit layout written from the spec
 * table (generalized so that surrogate code points D800-DFFF, which are legal labels of a Lucene
 * UTF-32 automaton, encode as ED A0..BF xx). The expected byte language of makeCharRange(lo,hi) is
 * exactly { enc(c) : lo <= c <= hi }.
 */
public class TestUtf32ToUtf8OverlongVerify extends LuceneTestCase {

  // ---- independent encoder (RFC 3629 table, no Lucene code) ----
  static byte[] enc(int c) {
    if (c < 0x80) return new byte[] {(byte) c};
    if (c < 0x800) return new byte[] {(byte) (0xC0 | (c >> 6)), (byte) (0x80 | (c & 0x3F))};
    if (c < 0x10000)
      return new byte[] {
        (byte) (0xE0 | (c >> 12)), (byte) (0x80 | ((c >> 6) & 0x3F)), (byte) (0x80 | (c & 0x3F))
      };
    return new byte[] {
      (byte) (0xF0 | (c >> 18)),
      (byte) (0x80 | ((c >> 12) & 0x3F)),
      (byte) (0x80 | ((c >> 6) & 0x3F)),
      (byte) (0x80 | (c & 0x3F))
    };
  }

  static boolean run(ByteRunAutomaton a, int... b) {
    byte[] bytes = new byte[b.length];
    for (int i = 0; i < b.length; i++) bytes[i] = (byte) b[i];
    return a.run(bytes, 0, bytes.length);
  }

  static ByteRunAutomaton bra(int lo, int hi) {
    return new ByteRunAutomaton(Automata.makeCharRange(lo, hi));
  }

  // ---------------------------------------------------------------------------------------
  // 1. Minimal deterministic repros (expected to FAIL on main 7cecf79523)
  // ---------------------------------------------------------------------------------------

  /** [U+0000, U+1000]: E0 80 80 is the overlong form of U+0000; must be rejected. */
  public void testRange0to1000RejectsOverlong3Byte() {
    ByteRunAutomaton a = bra(0, 0x1000);
    assertTrue("sanity: canonical U+0000 accepted", run(a, 0x00));
    assertTrue("sanity: canonical U+1000 (E1 80 80) accepted", run(a, 0xE1, 0x80, 0x80));
    assertFalse("E0 80 80 (overlong U+0000) must be rejected", run(a, 0xE0, 0x80, 0x80));
  }

  /** makeAnyChar(): F0 80 80 80 (overlong U+0000) and E0 80 80 must be rejected. */
  public void testAnyCharRejectsOverlong() {
    ByteRunAutomaton a = new ByteRunAutomaton(Automata.makeAnyChar());
    assertFalse("E0 80 80 must be rejected by makeAnyChar", run(a, 0xE0, 0x80, 0x80));
    assertFalse("F0 80 80 80 must be rejected by makeAnyChar", run(a, 0xF0, 0x80, 0x80, 0x80));
  }

  /** Regex "." (what RegexpQuery("."), WildcardQuery("?") compile to). */
  public void testRegexDotRejectsOverlong() {
    Automaton dot = new RegExp(".").toAutomaton();
    ByteRunAutomaton a = new ByteRunAutomaton(dot);
    assertFalse("regex . must reject E0 9F BF", run(a, 0xE0, 0x9F, 0xBF));
    assertFalse("regex . must reject F0 8F BF BF", run(a, 0xF0, 0x8F, 0xBF, 0xBF));
  }

  /**
   * Even under Lucene's OWN lenient decoder (UnicodeUtil via BytesRef.utf8ToString), the accepted
   * string decodes to a code point OUTSIDE the range: E0 80 80 -> U+0000 not in [U+007F,U+1000].
   */
  public void testAcceptedStringDecodesOutsideRange() {
    ByteRunAutomaton a = bra(0x7F, 0x1000);
    BytesRef overlong = new BytesRef(new byte[] {(byte) 0xE0, (byte) 0x80, (byte) 0x80});
    String decoded = overlong.utf8ToString();
    assertEquals("Lucene decodes E0 80 80 to U+0000", "\u0000", decoded);
    assertFalse(
        "range [7F,1000] accepted a term Lucene itself decodes to U+0000",
        a.run(overlong.bytes, overlong.offset, overlong.length));
  }

  /** Same shape as upstream TestUnicodeUtil#testUTF8SpanMultipleBytes, but spanning 1->3 bytes. */
  public void testCompiledAutomatonFiniteStringCount() {
    CompiledAutomaton c = new CompiledAutomaton(Automata.makeCharRange(0x7F, 0x1000));
    FiniteStringsIterator it = new FiniteStringsIterator(c.automaton);
    int total = 0, invalid = 0;
    for (IntsRef r = it.next(); r != null; r = it.next()) {
      total++;
      if (!isCanonicalInRange(r, 0x7F, 0x1000)) invalid++;
    }
    System.out.println("[FiniteStrings 7F..1000] total=" + total + " invalid=" + invalid);
    assertEquals("not-canonical strings", 0, invalid);
    assertEquals(0x1000 - 0x7F + 1, total);
  }

  /**
   * End-to-end through the index: a binary (non-UTF-8) term in a StringField is matched by
   * Unicode-built queries. Term E0 80 80 is decoded by Lucene as "\u0000".
   */
  public void testIndexBinaryTermMatchedByUnicodeQueries() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document d1 = new Document();
      d1.add(new StringField("f", "a", Field.Store.NO));
      w.addDocument(d1);
      Document d2 = new Document();
      d2.add(
          new StringField(
              "f",
              new BytesRef(new byte[] {(byte) 0xE0, (byte) 0x80, (byte) 0x80}),
              Field.Store.NO));
      w.addDocument(d2);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int regexDot = s.count(new RegexpQuery(new Term("f", ".")));
        int wildQ = s.count(new WildcardQuery(new Term("f", "?")));
        // any single code point except U+0000:
        int notNul =
            s.count(new AutomatonQuery(new Term("f"), Automata.makeCharRange(1, 0x10FFFF)));
        System.out.println(
            "[index] RegexpQuery(.)=" + regexDot + " WildcardQuery(?)=" + wildQ
                + " AutomatonQuery([U+0001-U+10FFFF])=" + notNul + " (expected 1 each)");
        assertEquals("AutomatonQuery [U+0001..U+10FFFF]", 1, notNul);
        assertEquals("RegexpQuery .", 1, regexDot);
        assertEquals("WildcardQuery ?", 1, wildQ);
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // 2. Boundary controls (expected to PASS on main: the #12472 special cases)
  // ---------------------------------------------------------------------------------------

  public void testControlsThatAreCorrectOnMain() {
    // end lead byte exactly E0 -> #12472 special case 0xA0 applies
    assertFalse(run(bra(0, 0x0FFF), 0xE0, 0x80, 0x80));
    // lo >= U+0800: start byte sequence already >= E0 A0 80
    assertFalse(run(bra(0x800, 0x1000), 0xE0, 0x80, 0x80));
    // end lead byte exactly F0 -> #12472 special case 0x90 applies
    assertFalse(run(bra(0xFFFF, 0x3FFFF), 0xF0, 0x80, 0x80, 0x80));
    // lo >= U+10000
    assertFalse(run(bra(0x10000, 0x40000), 0xF0, 0x80, 0x80, 0x80));
    // two-byte overlongs C0/C1 are never produced
    ByteRunAutomaton any = new ByteRunAutomaton(Automata.makeAnyChar());
    assertFalse(run(any, 0xC0, 0x80));
    assertFalse(run(any, 0xC1, 0xBF));
    // beyond U+10FFFF never produced
    assertFalse(run(any, 0xF4, 0x90, 0x80, 0x80));
    assertFalse(run(any, 0xF5, 0x80, 0x80, 0x80));
  }

  // ---------------------------------------------------------------------------------------
  // 3. Exact characterization over boundary grid + seeded random ranges.
  //    L = language of determinized convert(makeCharRange(lo,hi)).
  //    Check: every enc(c), lo<=c<=hi, accepted; count overlong-3 box (E0 80..9F xx) and
  //    overlong-4 box (F0 80..8F xx xx) accepted; then |L| (path count of acyclic DFA) must equal
  //    (hi-lo+1) + overlong3 + overlong4  => L has no other garbage.
  //    Then assert the claimed iff predicate for both boxes. (PASSES on main if claim is right.)
  // ---------------------------------------------------------------------------------------

  static final int[] GRID = {
    0, 1, 0x7E, 0x7F, 0x80, 0x81, 0x7FE, 0x7FF, 0x800, 0x801, 0xFFF, 0x1000, 0x1001, 0x1FFF,
    0x2000, 0xD7FF, 0xD800, 0xDFFF, 0xE000, 0xEFFF, 0xF000, 0xFFFE, 0xFFFF, 0x10000, 0x10001,
    0x3FFFF, 0x40000, 0x40001, 0xFFFFF, 0x100000, 0x10FFFE, 0x10FFFF
  };

  public void testExactCharacterizationMatchesClaimedIff() {
    List<int[]> ranges = new ArrayList<>();
    for (int i = 0; i < GRID.length; i++)
      for (int j = i; j < GRID.length; j++) ranges.add(new int[] {GRID[i], GRID[j]});
    Random rnd = new Random(0xC0FFEEL); // fixed seed, deterministic
    for (int k = 0; k < 300; k++) {
      int a = rnd.nextInt(0x110000), b = rnd.nextInt(0x110000);
      ranges.add(new int[] {Math.min(a, b), Math.max(a, b)});
    }
    int bad3 = 0, bad4 = 0, mismatch = 0;
    long canonicalChecked = 0;
    for (int[] r : ranges) {
      int lo = r[0], hi = r[1];
      Automaton dfa =
          Operations.determinize(
              new UTF32ToUTF8().convert(Automata.makeCharRange(lo, hi)), Integer.MAX_VALUE);
      ByteRunAutomaton run = new ByteRunAutomaton(dfa, true);
      for (int c = lo; c <= hi; c++) {
        byte[] e = enc(c);
        canonicalChecked++;
        if (!run.run(e, 0, e.length)) fail("canonical enc(U+" + Integer.toHexString(c) + ") rejected");
      }
      long ov3 = 0, ov4 = 0;
      for (int b1 = 0x80; b1 <= 0x9F; b1++)
        for (int b2 = 0x80; b2 <= 0xBF; b2++)
          if (run(run, 0xE0, b1, b2)) ov3++;
      for (int b1 = 0x80; b1 <= 0x8F; b1++)
        for (int b2 = 0x80; b2 <= 0xBF; b2++)
          for (int b3 = 0x80; b3 <= 0xBF; b3++) if (run(run, 0xF0, b1, b2, b3)) ov4++;
      long size = countAccepted(dfa);
      if (size != (long) (hi - lo + 1) + ov3 + ov4) {
        mismatch++;
        System.out.println(
            "[garbage outside boxes] lo=" + hex(lo) + " hi=" + hex(hi) + " |L|=" + size
                + " n=" + (hi - lo + 1) + " ov3=" + ov3 + " ov4=" + ov4);
      }
      // -Dverify.expectFixed=true when running against the patched class: then no overlong at all
      boolean fixed = Boolean.getBoolean("verify.expectFixed");
      boolean pred3 = !fixed && lo < 0x800 && hi >= 0x1000;
      boolean pred4 = !fixed && lo < 0x10000 && hi >= 0x40000;
      assertEquals("ov3 predicate lo=" + hex(lo) + " hi=" + hex(hi), pred3 ? 2048 : 0, ov3);
      assertEquals("ov4 predicate lo=" + hex(lo) + " hi=" + hex(hi), pred4 ? 65536 : 0, ov4);
      if (ov3 > 0) bad3++;
      if (ov4 > 0) bad4++;
    }
    System.out.println(
        "[characterization] ranges=" + ranges.size() + " withOverlong3=" + bad3
            + " withOverlong4=" + bad4 + " garbageOutsideBoxes=" + mismatch
            + " canonicalEncodingsChecked=" + canonicalChecked);
    assertEquals(0, mismatch);
  }

  /** Strict version of the above: no range may accept any overlong (FAILS on main). */
  public void testNoRangeAcceptsOverlongStrict() {
    int bad = 0;
    for (int i = 0; i < GRID.length; i++)
      for (int j = i; j < GRID.length; j++) {
        ByteRunAutomaton a = bra(GRID[i], GRID[j]);
        if (run(a, 0xE0, 0x80, 0x80) || run(a, 0xE0, 0x9F, 0xBF) || run(a, 0xF0, 0x80, 0x80, 0x80)
            || run(a, 0xF0, 0x8F, 0xBF, 0xBF)) bad++;
      }
    assertEquals("grid ranges accepting an overlong sequence", 0, bad);
  }

  // ---- helpers ----
  static String hex(int x) {
    return "U+" + Integer.toHexString(x).toUpperCase();
  }

  static boolean isCanonicalInRange(IntsRef r, int lo, int hi) {
    for (int c = lo; c <= hi; c++) {
      byte[] e = enc(c);
      if (e.length != r.length) continue;
      boolean eq = true;
      for (int k = 0; k < e.length && eq; k++) eq = (e[k] & 0xFF) == r.ints[r.offset + k];
      if (eq) return true;
    }
    return false;
  }

  /** Number of accepted strings of an acyclic DFA (each distinct path = distinct string). */
  static long countAccepted(Automaton dfa) {
    long[] memo = new long[dfa.getNumStates()];
    java.util.Arrays.fill(memo, -1);
    return count(dfa, 0, memo, new boolean[dfa.getNumStates()]);
  }

  static long count(Automaton a, int s, long[] memo, boolean[] onStack) {
    if (memo[s] >= 0) return memo[s];
    if (onStack[s]) throw new IllegalStateException("cycle");
    onStack[s] = true;
    long n = a.isAccept(s) ? 1 : 0;
    Transition t = new Transition();
    int nt = a.initTransition(s, t);
    for (int i = 0; i < nt; i++) {
      a.getNextTransition(t);
      n += (long) (t.max - t.min + 1) * count(a, t.dest, memo, onStack);
    }
    onStack[s] = false;
    memo[s] = n;
    return n;
  }
}
