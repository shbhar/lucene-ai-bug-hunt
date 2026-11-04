package org.apache.lucene.geo;

import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestXYTessellatorMonitor extends LuceneTestCase {

  public void testMonitorOnXYPolygonOutsideLatLonRange() {
    XYPolygon triangle = new XYPolygon(new float[] {0, 500, 0, 0}, new float[] {0, 0, 50, 0});
    Tessellator.Monitor noOp =
        new Tessellator.Monitor() {
          @Override
          public void currentState(String status, List<Point> points, List<Tessellator.Triangle> t) {}

          @Override
          public void startSplit(String status, List<Point> left, List<Point> right) {}

          @Override
          public void endSplit(String status) {}
        };
    // expected: 1 triangle, as without a monitor; actual: IllegalArgumentException "invalid longitude 500.0"
    assertEquals(1, Tessellator.tessellate(triangle, false, noOp).size());
  }
}
