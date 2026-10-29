package org.apache.lucene.facet.range;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDoubleRangeExclusiveInfinityBound extends LuceneTestCase {

  public void testExclusiveMinAtPositiveInfinityMatchesNothing() {
    // "value > +Infinity" matches no double: expected IllegalArgumentException or accept(+Inf) false
    DoubleRange range;
    try {
      range = new DoubleRange("x", Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, true);
    } catch (IllegalArgumentException acceptable) {
      return;
    }
    assertFalse(
        "(+Inf, +Inf] should match nothing, but accept(+Inf) returned true",
        range.accept(Double.POSITIVE_INFINITY));
  }
}
