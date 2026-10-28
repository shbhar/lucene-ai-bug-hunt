import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.GeoUtils;

/**
 * Compares the stock Component2D.pointInTriangle (from the Lucene jar on the classpath) with the proposed
 * winding-independent predicate on every triangle a, b, c and point p of the integer grid {-R..R}^2.
 * Prints how many answers differ and whether every difference is "p equals vertex b of a clockwise triangle".
 */
public class PredicateDiff {
  static boolean fixed(double minX, double maxX, double minY, double maxY, double x, double y,
      double aX, double aY, double bX, double bY, double cX, double cY) {
    if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
      int a = GeoUtils.orient(x, y, aX, aY, bX, bY);
      int b = GeoUtils.orient(x, y, bX, bY, cX, cY);
      int c = GeoUtils.orient(x, y, cX, cY, aX, aY);
      return (a >= 0 && b >= 0 && c >= 0) || (a <= 0 && b <= 0 && c <= 0);
    }
    return false;
  }

  public static void main(String[] args) {
    int r = args.length > 0 ? Integer.parseInt(args[0]) : 3;
    int n = 2 * r + 1, pts = n * n;
    long cases = 0, diffs = 0, diffsAtCwB = 0, cwBCases = 0, stockTrueFixedFalse = 0;
    for (int ia = 0; ia < pts; ia++)
      for (int ib = 0; ib < pts; ib++)
        for (int ic = 0; ic < pts; ic++) {
          double aX = ia % n - r, aY = ia / n - r, bX = ib % n - r, bY = ib / n - r, cX = ic % n - r, cY = ic / n - r;
          double minX = Math.min(aX, Math.min(bX, cX)), maxX = Math.max(aX, Math.max(bX, cX));
          double minY = Math.min(aY, Math.min(bY, cY)), maxY = Math.max(aY, Math.max(bY, cY));
          int w = GeoUtils.orient(aX, aY, bX, bY, cX, cY);
          for (int ip = 0; ip < pts; ip++) {
            double x = ip % n - r, y = ip / n - r;
            cases++;
            boolean atCwB = w < 0 && x == bX && y == bY;
            if (atCwB) cwBCases++;
            boolean s = Component2D.pointInTriangle(minX, maxX, minY, maxY, x, y, aX, aY, bX, bY, cX, cY);
            boolean f = fixed(minX, maxX, minY, maxY, x, y, aX, aY, bX, bY, cX, cY);
            if (s != f) {
              diffs++;
              if (atCwB && !s && f) diffsAtCwB++;
              if (s && !f) stockTrueFixedFalse++;
            }
          }
        }
    System.out.println("grid {-" + r + ".." + r + "}^2: cases=" + cases + " differences=" + diffs
        + " of_which_p_is_vertex_b_of_clockwise_triangle=" + diffsAtCwB
        + " stock_true_fixed_false=" + stockTrueFixedFalse
        + " cases_with_p_at_vertex_b_of_clockwise_triangle=" + cwBCases);
    System.out.println(diffs == diffsAtCwB && diffsAtCwB == cwBCases
        ? "RESULT: the predicates differ exactly on 'p equals vertex b of a clockwise triangle' (stock false, fix true)"
        : "RESULT: other differences exist");
  }
}
