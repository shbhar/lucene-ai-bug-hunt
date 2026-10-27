/*
 * Independent verification test. Written from scratch; not derived from the
 * reporter's test. Drives SparseFixedBitSet directly with lengths near IndexWriter.MAX_DOCS.
 *
 * Hypothesis under test: SparseFixedBitSet.firstClearInBlock computes
 *   blockEnd = Math.min(blockBase + 4096, length)
 * in int arithmetic; for the last 4096-bit block of a set whose length > 2^31 - 4096,
 * blockBase + 4096 == 2^31 wraps to Integer.MIN_VALUE, so every clear bit in that block is missed.
 */
package org.apache.lucene.util;

import java.io.IOException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestVerifySfbsLastBlockClearBit extends LuceneTestCase {

  /** First index of the last 4096-bit block that can exist in an int-indexed set: 2^31 - 4096. */
  static final int LAST_BLOCK_BASE = Integer.MAX_VALUE - 4095; // 2147479552

  static final int NMD = DocIdSetIterator.NO_MORE_DOCS;

  private static void log(String s) {
    System.out.println("[verify] " + s);
  }

  /** Reference answer computed with get(), independent of nextClearBit. */
  private static int refNextClear(BitSet bs, int from, int upTo) {
    for (int i = from; i < upTo; i++) {
      if (bs.get(i) == false) {
        return i;
      }
    }
    return NMD;
  }

  // ---- 1. single set bit in the last block, length = MAX_DOCS (documented maximum maxDoc) ----
  public void testSingleSetBitInLastBlockAtMaxDocs() {
    final int length = IndexWriter.MAX_DOCS; // 2147483519
    final int d = length - 1000; // 2147482519, inside the last block
    assertTrue(d >= LAST_BLOCK_BASE);
    SparseFixedBitSet sparse = new SparseFixedBitSet(length);
    sparse.set(d);
    FixedBitSet fixed = new FixedBitSet(length); // control: same content, other impl
    fixed.set(d);
    int sparseRes = sparse.nextClearBit(d);
    int fixedRes = fixed.nextClearBit(d);
    int ref = refNextClear(sparse, d, length);
    log("length=" + length + " d=" + d + " SFBS.nextClearBit(d)=" + sparseRes
        + " FixedBitSet.nextClearBit(d)=" + fixedRes + " reference(get)=" + ref);
    assertEquals("control FixedBitSet", d + 1, fixedRes);
    assertEquals("reference via get()", d + 1, ref);
    assertEquals("SparseFixedBitSet.nextClearBit(d)", d + 1, sparseRes);
  }

  // ---- 2. completely empty set: the first clear bit at/after i is i itself ----
  public void testEmptySetLastBlock() {
    final int length = IndexWriter.MAX_DOCS;
    SparseFixedBitSet sparse = new SparseFixedBitSet(length);
    int i = LAST_BLOCK_BASE + 17;
    int r1 = sparse.nextClearBit(i);
    int r2 = sparse.nextClearBit(i, i + 5);
    int r3 = sparse.nextClearBit(LAST_BLOCK_BASE - 1); // penultimate block: control
    log("empty length=" + length + " nextClearBit(" + i + ")=" + r1 + " nextClearBit(" + i + ","
        + (i + 5) + ")=" + r2 + " nextClearBit(" + (LAST_BLOCK_BASE - 1) + ")=" + r3);
    assertEquals("control, penultimate block", LAST_BLOCK_BASE - 1, r3);
    assertEquals("nextClearBit(i) on empty set", i, r1);
    assertEquals("nextClearBit(i, i+5) on empty set", i, r2);
  }

  // ---- 3. search that starts in the penultimate block and must continue into the last block ----
  public void testRunEndsExactlyAtLastBlockBoundary() {
    final int length = IndexWriter.MAX_DOCS;
    SparseFixedBitSet sparse = new SparseFixedBitSet(length);
    // set the last 10 bits of the penultimate block; first clear bit is LAST_BLOCK_BASE
    sparse.set(LAST_BLOCK_BASE - 10, LAST_BLOCK_BASE);
    int r = sparse.nextClearBit(LAST_BLOCK_BASE - 10);
    log("bits [" + (LAST_BLOCK_BASE - 10) + "," + LAST_BLOCK_BASE + ") set; nextClearBit="
        + r + " expected=" + LAST_BLOCK_BASE);
    assertEquals(LAST_BLOCK_BASE, r);
  }

  // ---- 4. exact threshold: lengths around 2^31 - 4096 on an empty set, query last valid index ----
  public void testThreshold() {
    int[] lengths = {
      LAST_BLOCK_BASE - 1, LAST_BLOCK_BASE, LAST_BLOCK_BASE + 1, IndexWriter.MAX_DOCS,
      Integer.MAX_VALUE
    };
    StringBuilder failures = new StringBuilder();
    for (int length : lengths) {
      SparseFixedBitSet sparse = new SparseFixedBitSet(length);
      int q = length - 1;
      int r = sparse.nextClearBit(q);
      boolean ok = r == q;
      log("threshold length=" + length + " (" + (length > LAST_BLOCK_BASE ? ">" : "<=")
          + " 2^31-4096) empty.nextClearBit(length-1)=" + r + (ok ? " OK" : " WRONG"));
      if (!ok) {
        failures.append(length).append(' ');
      }
    }
    // Correct behaviour: on an empty set nextClearBit(q) == q for every length.
    assertEquals("lengths where empty.nextClearBit(length-1) != length-1", "", failures.toString());
  }

  // ---- 5. control at the largest safe length: same pattern as test 1, must pass ----
  public void testControlLargestSafeLength() {
    final int length = LAST_BLOCK_BASE; // 2^31 - 4096
    final int d = length - 1000;
    SparseFixedBitSet sparse = new SparseFixedBitSet(length);
    sparse.set(d);
    int r = sparse.nextClearBit(d);
    log("control length=" + length + " d=" + d + " nextClearBit(d)=" + r);
    assertEquals(d + 1, r);
  }

  // ---- 6. downstream: BitSetIterator.docIDRunEnd claims docs that are not in the set ----
  public void testDocIdRunEndOverclaims() throws IOException {
    final int length = IndexWriter.MAX_DOCS;
    final int d = length - 1000;
    SparseFixedBitSet sparse = new SparseFixedBitSet(length);
    sparse.set(d);
    BitSetIterator it = new BitSetIterator(sparse, 1);
    assertEquals(d, it.advance(d));
    int runEnd = it.docIDRunEnd();
    // count how many docs of [d, runEnd) are actually in the set
    int actuallySet = 0;
    for (int i = d; i < runEnd; i++) {
      if (sparse.get(i)) actuallySet++;
    }
    int next = it.nextDoc();

    FixedBitSet fixed = new FixedBitSet(length);
    fixed.set(d);
    BitSetIterator fit = new BitSetIterator(fixed, 1);
    assertEquals(d, fit.advance(d));
    int fixedRunEnd = fit.docIDRunEnd();

    log("SFBS docIDRunEnd at doc " + d + " = " + runEnd + " -> claimed run length "
        + (runEnd - d) + ", docs actually set in run = " + actuallySet
        + ", nextDoc() after d = " + next + "; FixedBitSet control docIDRunEnd = " + fixedRunEnd);
    assertEquals("FixedBitSet control", d + 1, fixedRunEnd);
    // DocIdSetIterator#docIDRunEnd contract: all docs in [docID(), docIDRunEnd()) match
    assertEquals("SparseFixedBitSet-backed BitSetIterator.docIDRunEnd", d + 1, runEnd);
  }
}
