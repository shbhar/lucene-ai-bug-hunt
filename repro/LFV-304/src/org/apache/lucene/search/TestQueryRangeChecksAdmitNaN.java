package org.apache.lucene.search;

import java.util.List;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQueryRangeChecksAdmitNaN extends LuceneTestCase {

  // expected IllegalArgumentException, as for any other out-of-range value; actual: NaN accepted
  public void testDisjunctionMaxQueryNaNTieBreaker() {
    expectThrows(
        IllegalArgumentException.class, () -> new DisjunctionMaxQuery(List.of(), Float.NaN));
  }

  public void testDistanceFeatureQueryNaNPivot() {
    expectThrows(
        IllegalArgumentException.class,
        () -> LatLonPoint.newDistanceFeatureQuery("f", 1, 0, 0, Double.NaN));
  }
}
