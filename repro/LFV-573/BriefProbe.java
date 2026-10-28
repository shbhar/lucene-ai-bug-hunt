import org.apache.lucene.geo.GeoUtils;
import org.apache.lucene.util.SloppyMath;

/**
 * Prints, for the two points of the repro, the line of sort keys around the radius: which adjacent
 * keys share one distance in metres, where the point's key is, which threshold
 * GeoUtils.distanceQuerySortKey returns, and which threshold a "largest key within the radius"
 * search returns. Public API only. Run against the stock jar (and, with the fix shadowed, the
 * "stock threshold" line shows the fixed method).
 */
public class BriefProbe {
  static final double C_LAT = -23.073417018167675, C_LON = -155.66040300764143;
  static final double IN_LAT = 49.503454877994955, IN_LON = -167.46806376613677;
  static final double OUT_LAT = 56.79421530570835, OUT_LON = 8.682850794866681;

  static double d(long bits) {
    return SloppyMath.haversinMeters(Double.longBitsToDouble(bits));
  }

  /** the largest key whose distance is <= radius (reference, linear scan near a start key) */
  static long largestWithin(double radius, long start) {
    long k = start;
    while (d(k) > radius) {
      k--;
    }
    while (d(k + 1) <= radius) {
      k++;
    }
    return k;
  }

  static void line(String label, long pointKey, double radius) {
    long t = Double.doubleToRawLongBits(GeoUtils.distanceQuerySortKey(radius));
    long tRef = largestWithin(radius, pointKey);
    long from = Math.min(Math.min(pointKey, t), tRef) - 3;
    long to = Math.max(Math.max(pointKey, t), tRef) + 3;
    System.out.println(label + ": radius = " + radius + " m");
    System.out.println("  key (offset from the point's key)   distance in metres     marks");
    for (long k = from; k <= to; k++) {
      double dk = d(k);
      StringBuilder marks = new StringBuilder();
      if (k == pointKey) marks.append(" POINT");
      if (k == t) marks.append(" THRESHOLD(distanceQuerySortKey)");
      if (k == tRef) marks.append(" LARGEST-KEY-WITHIN-RADIUS");
      if ((k & 7) == 0) marks.append(" [maskable]");
      System.out.printf(
          "  %+3d  %-22s  %-22s %s %s%n",
          k - pointKey,
          Double.longBitsToDouble(k),
          dk,
          dk == radius ? "== r" : (dk < radius ? "<  r" : ">  r"),
          marks);
    }
    double pk = Double.longBitsToDouble(pointKey);
    System.out.println(
        "  point distance = "
            + SloppyMath.haversinMeters(pk)
            + " m; oracle (distance <= radius) = "
            + (SloppyMath.haversinMeters(pk) <= radius)
            + "; query test (pointKey <= threshold) = "
            + (pk <= Double.longBitsToDouble(t))
            + "; with the largest key within the radius = "
            + (pk <= Double.longBitsToDouble(tRef)));
    System.out.println("  ulp of the radius = " + Math.ulp(radius) + " m");
  }

  public static void main(String[] args) {
    long inKey =
        Double.doubleToRawLongBits(SloppyMath.haversinSortKey(IN_LAT, IN_LON, C_LAT, C_LON));
    double dIn = SloppyMath.haversinMeters(C_LAT, C_LON, IN_LAT, IN_LON);
    line("IN point, radius = its distance (expect match)", inKey, dIn);

    long outKey =
        Double.doubleToRawLongBits(SloppyMath.haversinSortKey(OUT_LAT, OUT_LON, C_LAT, C_LON));
    double dOut = SloppyMath.haversinMeters(C_LAT, C_LON, OUT_LAT, OUT_LON);
    line("OUT point, radius = one ulp below its distance (expect no match)", outKey, Math.nextDown(dOut));
  }
}
