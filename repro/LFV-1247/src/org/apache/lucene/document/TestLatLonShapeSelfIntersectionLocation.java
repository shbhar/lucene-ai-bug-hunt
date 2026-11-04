package org.apache.lucene.document;

import org.apache.lucene.geo.Polygon;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLatLonShapeSelfIntersectionLocation extends LuceneTestCase {

  public void testRetracedEdgeReportsFiniteLocation() {
    // expected: IllegalArgumentException with a finite location; actual: lat=Infinity lon=Infinity (-da) or AssertionError (-ea)
    Polygon polygon =
        new Polygon(
            new double[] {0, 7, 4, 3.85, 1.4, 2, 0}, new double[] {0, 1, 8, 0.55, 0.2, -7, 0});
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> LatLonShape.createIndexableFields("shape", polygon, true));
    assertFalse(e.getMessage(), e.getMessage().contains("Infinity"));
  }
}
