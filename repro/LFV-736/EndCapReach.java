package endcapprobe;

import java.util.Locale;
import org.apache.lucene.spatial3d.geom.GeoPath;
import org.apache.lucene.spatial3d.geom.GeoPathFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;

/**
 * How far does a geo3d path reach beyond each end, along bearings within 60 degrees of the outward
 * direction? Public API only; points come from PlanetModel.surfacePointOnBearing (Vincenty on the
 * ellipsoid; bearing 0 = north, pi/2 = east in the implementation). The width conversion is the one
 * Geo3DUtil.fromPath uses. Expected: every reach equals the width.
 */
public class EndCapReach {

  static GeoPath path(PlanetModel pm, double w, double[] lats, double[] lons) {
    GeoPoint[] pts = new GeoPoint[lats.length];
    for (int i = 0; i < pts.length; i++) {
      pts[i] = new GeoPoint(pm, Math.toRadians(lats[i]), Math.toRadians(lons[i]));
    }
    return GeoPathFactory.makeGeoPath(pm, w / (pm.getMeanRadius() * pm.xyScaling), pts);
  }

  /** Largest scanned distance (m) along the bearing from p that is still within the path. */
  static double reach(PlanetModel pm, GeoPath path, GeoPoint p, double bearing, double w) {
    double step = w / 400;
    double last = 0;
    for (double d = step; d <= Math.max(5 * w, 80000); d += step) {
      if (path.isWithin(pm.surfacePointOnBearing(p, d / pm.getMeanRadius(), bearing))) {
        last = d;
      }
    }
    return last;
  }

  /** Bearing from b that points away from its neighbour a (maximises the distance to a). */
  static double outward(PlanetModel pm, GeoPoint a, GeoPoint b) {
    double best = 0;
    double bestDist = -1;
    for (double deg = 0; deg < 360; deg += 0.25) {
      double brg = Math.toRadians(deg);
      double dist = pm.surfaceDistance(a, pm.surfacePointOnBearing(b, 1e-5, brg));
      if (dist > bestDist) {
        bestDist = dist;
        best = brg;
      }
    }
    return best;
  }

  static void probe(PlanetModel pm, String name, double w, double[] lats, double[] lons) {
    GeoPath path = path(pm, w, lats, lons);
    int n = lats.length - 1;
    GeoPoint a = new GeoPoint(pm, Math.toRadians(lats[0]), Math.toRadians(lons[0]));
    GeoPoint b = new GeoPoint(pm, Math.toRadians(lats[n]), Math.toRadians(lons[n]));
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(Locale.ROOT, "%-34s w=%6.0f m:", name, w));
    for (int end = 0; end < 2; end++) {
      GeoPoint p = end == 0 ? a : b;
      GeoPoint nb =
          end == 0
              ? new GeoPoint(pm, Math.toRadians(lats[1]), Math.toRadians(lons[1]))
              : new GeoPoint(pm, Math.toRadians(lats[n - 1]), Math.toRadians(lons[n - 1]));
      double out = outward(pm, nb, p);
      double min = Double.MAX_VALUE;
      double max = 0;
      double straight = 0;
      for (int off = -60; off <= 60; off += 15) {
        double r = reach(pm, path, p, out + Math.toRadians(off), w);
        min = Math.min(min, r);
        max = Math.max(max, r);
        if (off == 0) {
          straight = r;
        }
      }
      sb.append(
          String.format(
              Locale.ROOT,
              "  %s (outward %5.1f deg): straight %.2f w, min %.2f w, max %.2f w;",
              end == 0 ? "start" : "end",
              Math.toDegrees(out),
              straight / w,
              min / w,
              max / w));
    }
    System.out.println(sb);
  }

  public static void main(String[] args) {
    PlanetModel wgs = PlanetModel.WGS84;
    double r = wgs.getMeanRadius();
    PlanetModel sphere = new PlanetModel(r, r);
    for (double w : new double[] {1000, 20000, 200000}) {
      probe(wgs, "WGS84 N (44N,20E)->(45N,20E)", w, new double[] {44, 45}, new double[] {20, 20});
    }
    probe(wgs, "WGS84 S (45N,20E)->(44N,20E)", 20000, new double[] {45, 44}, new double[] {20, 20});
    probe(wgs, "WGS84 S hemi (44S,20E)->(45S,20E)", 20000, new double[] {-44, -45}, new double[] {20, 20});
    probe(wgs, "WGS84 NE (44N,20E)->(45N,21.4E)", 20000, new double[] {44, 45}, new double[] {20, 21.4});
    probe(wgs, "WGS84 E (45N,20E)->(45N,21.27E)", 20000, new double[] {45, 45}, new double[] {20, 21.27});
    probe(wgs, "WGS84 N (0N,20E)->(1N,20E)", 20000, new double[] {0, 1}, new double[] {20, 20});
    probe(wgs, "WGS84 N (60N,20E)->(61N,20E)", 20000, new double[] {60, 61}, new double[] {20, 20});
    probe(
        wgs,
        "WGS84 3 pts (44N,20E)(45N,20E)(45N,21.27E)",
        20000,
        new double[] {44, 45, 45},
        new double[] {20, 20, 21.27});
    probe(sphere, "sphere N (44N,20E)->(45N,20E)", 20000, new double[] {44, 45}, new double[] {20, 20});
    probe(sphere, "sphere NE (44N,20E)->(45N,21.4E)", 20000, new double[] {44, 45}, new double[] {20, 21.4});
  }
}
