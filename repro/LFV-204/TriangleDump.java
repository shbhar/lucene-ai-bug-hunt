import java.util.List;
import org.apache.lucene.document.ShapeField;
import org.apache.lucene.geo.GeoUtils;
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYEncodingUtils;
import org.apache.lucene.geo.XYPolygon;

/**
 * Prints, for the two XY repro polygons, each tessellation triangle with its "edge belongs to the
 * polygon" flags as the tessellator produced them, its orientation in encoded (sortable int)
 * space, and the flags after encodeTriangle/decodeTriangle mapped back to the same geometric
 * edges. Exit 1 if any decoded flag differs from the tessellator's flag for the same edge.
 */
public class TriangleDump {
  static String pt(int x, int y) {
    return "(" + XYEncodingUtils.decode(x) + "," + XYEncodingUtils.decode(y) + ")";
  }

  public static void main(String[] args) {
    XYPolygon[] polys = {
      new XYPolygon(new float[] {6, -1, -8, 0, 6}, new float[] {0, 7, 8, 1, 0}),
      new XYPolygon(new float[] {3, 6, 8, 1, 3}, new float[] {3, 0, 5, 8, 3})
    };
    int wrong = 0;
    for (XYPolygon p : polys) {
      System.out.println("polygon " + p);
      List<Tessellator.Triangle> ts = Tessellator.tessellate(p, true);
      for (Tessellator.Triangle t : ts) {
        int[] x = {t.getEncodedX(0), t.getEncodedX(1), t.getEncodedX(2)};
        int[] y = {t.getEncodedY(0), t.getEncodedY(1), t.getEncodedY(2)};
        boolean[] f = {t.isEdgefromPolygon(0), t.isEdgefromPolygon(1), t.isEdgefromPolygon(2)};
        int orient = GeoUtils.orient(x[0], y[0], x[1], y[1], x[2], y[2]);
        byte[] b = new byte[7 * Integer.BYTES];
        ShapeField.encodeTriangle(b, y[0], x[0], f[0], y[1], x[1], f[1], y[2], x[2], f[2]);
        ShapeField.DecodedTriangle d = new ShapeField.DecodedTriangle();
        ShapeField.decodeTriangle(b, d);
        int[][] dv = {{d.aX, d.aY}, {d.bX, d.bY}, {d.cX, d.cY}};
        boolean[] df = {d.ab, d.bc, d.ca};
        StringBuilder sb = new StringBuilder();
        sb.append("  triangle ").append(pt(x[0], y[0])).append(' ').append(pt(x[1], y[1]))
            .append(' ').append(pt(x[2], y[2])).append(" encoded-orientation=")
            .append(orient == -1 ? "CW" : orient == 1 ? "CCW" : "collinear").append('\n');
        for (int e = 0; e < 3; e++) {
          int u = e, w = (e + 1) % 3;
          boolean decoded = false;
          for (int k = 0; k < 3; k++) {
            int[] p0 = dv[k], p1 = dv[(k + 1) % 3];
            if ((p0[0] == x[u] && p0[1] == y[u] && p1[0] == x[w] && p1[1] == y[w])
                || (p0[0] == x[w] && p0[1] == y[w] && p1[0] == x[u] && p1[1] == y[u])) {
              decoded = df[k];
            }
          }
          boolean bad = decoded != f[e];
          if (bad) wrong++;
          sb.append("    edge ").append(pt(x[u], y[u])).append("-").append(pt(x[w], y[w]))
              .append(" polygon-edge=").append(f[e]).append(" stored-flag=").append(decoded)
              .append(bad ? "   <-- WRONG" : "").append('\n');
        }
        System.out.print(sb);
      }
    }
    System.out.println("wrong_flags=" + wrong);
    System.exit(wrong == 0 ? 0 : 1);
  }
}
