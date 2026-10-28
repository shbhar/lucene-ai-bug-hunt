package org.apache.lucene.document;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.geo.XYEncodingUtils;
import org.apache.lucene.geo.XYPoint;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

/** Prints every triangle XYShape indexes for a few polygons, how often each is indexed, and the doc values. */
public class CureDuplicateProbe {
  static void dump(String name, XYPolygon p) {
    IndexableField[] fields = XYShape.createIndexableFields("s", p);
    Map<BytesRef, Integer> count = new HashMap<>();
    Map<BytesRef, String> text = new HashMap<>();
    double areaAll = 0;
    for (IndexableField f : fields) {
      BytesRef b = BytesRef.deepCopyOf(f.binaryValue());
      ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
      ShapeField.decodeTriangle(b.bytes, t);
      double ax = XYEncodingUtils.decode(t.aX), ay = XYEncodingUtils.decode(t.aY);
      double bx = XYEncodingUtils.decode(t.bX), by = XYEncodingUtils.decode(t.bY);
      double cx = XYEncodingUtils.decode(t.cX), cy = XYEncodingUtils.decode(t.cY);
      double a = Math.abs(0.5 * ((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)));
      areaAll += a;
      count.merge(b, 1, Integer::sum);
      text.put(b, "(" + ax + "," + ay + ") (" + bx + "," + by + ") (" + cx + "," + cy + ") area=" + a);
    }
    double areaDistinct = 0;
    System.out.println(name + ": indexed=" + fields.length + " distinct=" + count.size());
    for (Map.Entry<BytesRef, Integer> e : count.entrySet()) {
      System.out.println("  " + e.getValue() + "x " + text.get(e.getKey()));
      String s = text.get(e.getKey());
      areaDistinct += Double.parseDouble(s.substring(s.indexOf("area=") + 5));
    }
    XYShapeDocValuesField dv = XYShape.createDocValueField("s", p);
    XYPoint c = dv.getCentroid();
    System.out.println("  area of indexed triangles=" + areaAll + " of distinct triangles=" + areaDistinct
        + "; doc values numberOfTerms=" + dv.numberOfTerms() + " centroid=(" + c.getX() + ", " + c.getY() + ")");
    try {
      XYShape.createIndexableFields("s", p, true);
      System.out.println("  checkSelfIntersections=true: accepted");
    } catch (IllegalArgumentException e) {
      System.out.println("  checkSelfIntersections=true: rejected: " + e.getMessage());
    }
  }

  public static void main(String[] args) {
    dump("self-crossing ring POLYGON((6 3, 0 6, 4 0, 3 4, 5 1, 3 2, 6 3))",
        new XYPolygon(new float[] {6, 0, 4, 3, 5, 3, 6}, new float[] {3, 6, 0, 4, 1, 2, 3}));
    XYPolygon hole = new XYPolygon(new float[] {2, 3, 2, 2}, new float[] {1, 2, 2, 1});
    dump("valid touching-hole POLYGON((4 2, 3 0, 1 2, 0 0, 0 4, 4 2), (2 1, 3 2, 2 2, 2 1))",
        new XYPolygon(new float[] {4, 3, 1, 0, 0, 4}, new float[] {2, 0, 2, 0, 4, 2}, hole));
    dump("control L-hexagon", new XYPolygon(new float[] {0, 4, 4, 2, 2, 0, 0}, new float[] {0, 0, 2, 2, 4, 4, 0}));
  }
}
