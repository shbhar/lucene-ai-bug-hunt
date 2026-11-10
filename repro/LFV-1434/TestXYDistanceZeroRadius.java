package org.apache.lucene.document;

import org.apache.lucene.tests.util.LuceneTestCase;

// Expected: radius 0 is accepted (javadoc: "must be non-negative and finite"); actual: IllegalArgumentException.
public class TestXYDistanceZeroRadius extends LuceneTestCase {

  public void testPointDistanceQuery() {
    XYPointField.newDistanceQuery("p", 1f, 2f, 0f);
  }

  public void testDocValuesDistanceQuery() {
    XYDocValuesField.newSlowDistanceQuery("p", 1f, 2f, 0f);
  }
}
