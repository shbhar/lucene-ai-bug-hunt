/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.util.automaton;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;

/**
 * UTF32ToUTF8 must convert a code-point automaton to the equivalent UTF-8 byte automaton (class
 * javadoc: "Converts UTF-32 automata to the equivalent UTF-8 representation"). The UTF-8 encoding
 * of a code point is unique, so the byte automaton of a range [lo, hi] must accept exactly the
 * shortest-form encodings of lo..hi and nothing else.
 *
 * <p>Overlong (non-shortest) forms used below:
 *
 * <ul>
 *   <li>{@code E0 80..9F xx}: 3-byte overlong forms of U+0000..U+07FF (the first real 3-byte code
 *       point, U+0800, is {@code E0 A0 80});
 *   <li>{@code F0 80..8F xx xx}: 4-byte overlong forms of U+0000..U+FFFF (the first real 4-byte
 *       code point, U+10000, is {@code F0 90 80 80}).
 * </ul>
 *
 * <p>GITHUB#12472 fixed this for ranges whose end lead byte is exactly E0 or F0. Ranges that start
 * below U+0800 and end at or above U+1000 (lead byte E1 or later), or start below U+10000 and end
 * at or above U+40000 (lead byte F1 or later), still accept these sequences. {@link
 * Automata#makeAnyChar()}, the regular expression "." and the wildcard "?" are such ranges.
 *
 * <p>Uses public API only (Automata, ByteRunAutomaton, RegExp, CompiledAutomaton,
 * FiniteStringsIterator, AutomatonQuery, RegexpQuery, WildcardQuery, IndexWriter) plus the test
 * framework's LuceneTestCase. It sits in package org.apache.lucene.util.automaton like Lucene's own
 * TestUTF32ToUTF8, but uses no package-private API.
 */
public class TestUTF32ToUTF8OverlongResidual extends LuceneTestCase {

  private static byte[] bytes(int... b) {
    byte[] out = new byte[b.length];
    for (int i = 0; i < b.length; i++) {
      out[i] = (byte) b[i];
    }
    return out;
  }

  private static boolean accepts(ByteRunAutomaton a, int... b) {
    byte[] in = bytes(b);
    return a.run(in, 0, in.length);
  }

  /** Lucene's own UTF-8 encoding of one code point. */
  private static boolean acceptsCodePoint(ByteRunAutomaton a, int codePoint) {
    BytesRef utf8 = new BytesRef(new String(Character.toChars(codePoint)));
    return a.run(utf8.bytes, utf8.offset, utf8.length);
  }

  /** A range spanning 1-byte to 3-byte code points: [U+0000, U+1000]. */
  public void testRangeSpanning1To3BytesRejectsOverlong() {
    ByteRunAutomaton a = new ByteRunAutomaton(Automata.makeCharRange(0, 0x1000));
    // the range itself is accepted
    assertTrue(acceptsCodePoint(a, 0x0000));
    assertTrue(acceptsCodePoint(a, 0x0800)); // E0 A0 80
    assertTrue(acceptsCodePoint(a, 0x1000)); // E1 80 80
    // overlong forms of U+0000 and U+07FF are not UTF-8 encodings of any code point
    assertFalse("E0 80 80 (overlong U+0000) must be rejected", accepts(a, 0xE0, 0x80, 0x80));
    assertFalse("E0 9F BF (overlong U+07FF) must be rejected", accepts(a, 0xE0, 0x9F, 0xBF));
  }

  /** A range spanning 3-byte to 4-byte code points: [U+0800, U+40000]. */
  public void testRangeSpanning3To4BytesRejectsOverlong() {
    ByteRunAutomaton a = new ByteRunAutomaton(Automata.makeCharRange(0x0800, 0x40000));
    assertTrue(acceptsCodePoint(a, 0x0800)); // E0 A0 80
    assertTrue(acceptsCodePoint(a, 0x10000)); // F0 90 80 80
    assertTrue(acceptsCodePoint(a, 0x40000)); // F1 80 80 80
    assertFalse(
        "F0 80 A0 80 (overlong U+0800) must be rejected", accepts(a, 0xF0, 0x80, 0xA0, 0x80));
    assertFalse(
        "F0 8F BF BF (overlong U+FFFF) must be rejected", accepts(a, 0xF0, 0x8F, 0xBF, 0xBF));
  }

  /**
   * The accepted overlong bytes decode, even with Lucene's own decoder, to a code point outside the
   * range: E0 80 80 decodes to U+0000, but the range is [U+007F, U+1000].
   */
  public void testAcceptedBytesDecodeOutsideTheRange() {
    BytesRef overlong = new BytesRef(bytes(0xE0, 0x80, 0x80));
    assertEquals("\u0000", overlong.utf8ToString());
    ByteRunAutomaton a = new ByteRunAutomaton(Automata.makeCharRange(0x7F, 0x1000));
    assertFalse(
        "[U+007F, U+1000] must not accept bytes that decode to U+0000",
        a.run(overlong.bytes, overlong.offset, overlong.length));
  }

  /** Automata.makeAnyChar(), used by the regexp "." and the wildcard "?". */
  public void testAnyCharRejectsOverlong() {
    ByteRunAutomaton a = new ByteRunAutomaton(Automata.makeAnyChar());
    assertTrue(acceptsCodePoint(a, 0x0800));
    assertTrue(acceptsCodePoint(a, 0x10000));
    assertFalse("E0 80 80 must be rejected by makeAnyChar", accepts(a, 0xE0, 0x80, 0x80));
    assertFalse("F0 80 80 80 must be rejected by makeAnyChar", accepts(a, 0xF0, 0x80, 0x80, 0x80));
  }

  /** The regular expression "." accepts any single code point, and nothing else. */
  public void testRegexpDotRejectsOverlong() {
    ByteRunAutomaton a = new ByteRunAutomaton(new RegExp(".").toAutomaton());
    assertFalse("regexp . must reject E0 9F BF", accepts(a, 0xE0, 0x9F, 0xBF));
    assertFalse("regexp . must reject F0 8F BF BF", accepts(a, 0xF0, 0x8F, 0xBF, 0xBF));
  }

  /**
   * A negated character class: "[^/]*" means "no '/'". E0 80 AF is an overlong form of '/' that
   * Lucene's own decoder turns into '/', so a term with it must not match.
   */
  public void testNegatedClassRejectsOverlongFormOfExcludedChar() {
    ByteRunAutomaton a = new ByteRunAutomaton(new RegExp("[^/]*").toAutomaton());
    BytesRef plain = new BytesRef("a/b");
    BytesRef overlong = new BytesRef(bytes('a', 0xE0, 0x80, 0xAF, 'b'));
    assertEquals("a/b", overlong.utf8ToString());
    assertTrue(a.run(new BytesRef("ab").bytes, 0, 2));
    assertFalse(a.run(plain.bytes, plain.offset, plain.length));
    assertFalse(
        "[^/]* must reject 61 E0 80 AF 62, which decodes to \"a/b\"",
        a.run(overlong.bytes, overlong.offset, overlong.length));
  }

  /**
   * Same shape as TestUnicodeUtil#testUTF8SpanMultipleBytes (added by GITHUB#12472), for a range
   * that spans 1-byte to 3-byte code points: the compiled byte automaton of [U+007F, U+1000] must
   * have exactly one string per code point, each the shortest form.
   */
  public void testCompiledRangeHasOneStringPerCodePoint() {
    int lo = 0x7F;
    int hi = 0x1000;
    CompiledAutomaton c = new CompiledAutomaton(Automata.makeCharRange(lo, hi));
    FiniteStringsIterator it = new FiniteStringsIterator(c.automaton);
    int total = 0;
    int notShortestForm = 0;
    for (IntsRef r = it.next(); r != null; r = it.next()) {
      total++;
      byte[] b = new byte[r.length];
      for (int i = 0; i < r.length; i++) {
        b[i] = (byte) r.ints[r.offset + i];
      }
      // decode leniently, then re-encode: only a shortest-form encoding round-trips
      BytesRef reencoded = new BytesRef(new BytesRef(b).utf8ToString());
      if (reencoded.bytesEquals(new BytesRef(b)) == false) {
        notShortestForm++;
      }
    }
    assertEquals(
        "strings that are not shortest-form UTF-8 (of " + total + " strings)", 0, notShortestForm);
    assertEquals("one string per code point", hi - lo + 1, total);
  }

  /**
   * End to end: a non-UTF-8 binary term (E0 80 80, which Lucene decodes as U+0000) next to "a".
   * Each query below accepts one code point from a set that excludes U+0000 or contains only real
   * UTF-8, so each must match only "a".
   */
  public void testUnicodeQueriesDoNotMatchOverlongBinaryTerm() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    Document d1 = new Document();
    d1.add(new StringField("f", "a", Field.Store.NO));
    w.addDocument(d1);
    Document d2 = new Document();
    d2.add(new StringField("f", new BytesRef(bytes(0xE0, 0x80, 0x80)), Field.Store.NO));
    w.addDocument(d2);
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    IndexSearcher s = newSearcher(r);
    int notNul = s.count(new AutomatonQuery(new Term("f"), Automata.makeCharRange(1, 0x10FFFF)));
    int regexpDot = s.count(new RegexpQuery(new Term("f", ".")));
    int wildcardQ = s.count(new WildcardQuery(new Term("f", "?")));
    r.close();
    dir.close();
    assertEquals(
        "matching documents",
        "AutomatonQuery[U+0001-U+10FFFF]=1 RegexpQuery(.)=1 WildcardQuery(?)=1",
        "AutomatonQuery[U+0001-U+10FFFF]="
            + notNul
            + " RegexpQuery(.)="
            + regexpDot
            + " WildcardQuery(?)="
            + wildcardQ);
  }

  /** Controls that pass: the cases GITHUB#12472 fixed, and 2-byte overlongs (C0, C1). */
  public void testControlsAlreadyCorrect() {
    // end lead byte exactly E0: special-cased by GITHUB#12472
    assertFalse(accepts(new ByteRunAutomaton(Automata.makeCharRange(0, 0x0FFF)), 0xE0, 0x80, 0x80));
    // range starts at U+0800: no 3-byte overlong
    assertFalse(
        accepts(new ByteRunAutomaton(Automata.makeCharRange(0x0800, 0x1000)), 0xE0, 0x80, 0x80));
    // end lead byte exactly F0: special-cased by GITHUB#12472
    assertFalse(
        accepts(
            new ByteRunAutomaton(Automata.makeCharRange(0xFFFF, 0x3FFFF)), 0xF0, 0x80, 0x80, 0x80));
    ByteRunAutomaton any = new ByteRunAutomaton(Automata.makeAnyChar());
    assertFalse(accepts(any, 0xC0, 0x80));
    assertFalse(accepts(any, 0xC1, 0xBF));
    assertFalse(accepts(any, 0xF4, 0x90, 0x80, 0x80)); // above U+10FFFF
  }
}
