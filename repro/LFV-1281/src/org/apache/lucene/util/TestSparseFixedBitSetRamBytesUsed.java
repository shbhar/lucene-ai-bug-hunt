package org.apache.lucene.util;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSparseFixedBitSetRamBytesUsed extends LuceneTestCase {

  public void testClearReleasesBlockBytes() {
    SparseFixedBitSet set = new SparseFixedBitSet(1);
    long fresh = set.ramBytesUsed();
    set.set(0);
    set.clear(0);
    // expected: the released block's bytes are subtracted (88); actual: they stay counted (112)
    assertEquals(fresh, set.ramBytesUsed());
  }
}
