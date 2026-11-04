package org.apache.lucene.spatial3d;

import org.apache.lucene.search.Query;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSignedZeroEqualsHashCode extends LuceneTestCase {

  public void testEqualQueriesHaveEqualHashCodes() {
    Query a = Geo3DPoint.newDistanceQuery("g", PlanetModel.WGS84, 0.0, 10.0, 1000.0);
    Query b = Geo3DPoint.newDistanceQuery("g", PlanetModel.WGS84, -0.0, 10.0, 1000.0);
    // expected: equal queries have equal hash codes; actual: equals() is true but hashCode() differs
    assertFalse(a.equals(b) && a.hashCode() != b.hashCode());
  }
}
