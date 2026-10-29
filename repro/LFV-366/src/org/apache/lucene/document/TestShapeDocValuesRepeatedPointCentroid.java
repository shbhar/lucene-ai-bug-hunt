package org.apache.lucene.document;

import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Point;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestShapeDocValuesRepeatedPointCentroid extends LuceneTestCase {

  public void testCentroidOfLineWithRepeatedPoint() {
    Line line = new Line(new double[] {40, 40}, new double[] {-70, -70});
    Point centroid = LatLonShape.createDocValueField("f", line).getCentroid();
    // expected: the line's only point (40, -70) within encoding error; actual: (0, 0)
    assertEquals("latitude", 40, centroid.getLat(), 1e-6);
    assertEquals("longitude", -70, centroid.getLon(), 1e-6);
  }
}
