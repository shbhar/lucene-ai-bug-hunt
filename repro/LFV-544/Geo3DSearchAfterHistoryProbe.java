// Release-portable main() probe for Lucene 8.5.0 .. main (the Geo3D sort factories take a PlanetModel
// argument since 8.5.0). No test framework. Exits 1 when the bug reproduces, 0 when every paging check
// passes, 3 when a control fails.
//
// For each Geo3D sort factory: six documents 3..8 degrees north of (0, 0), one value each, one segment.
// Page 1 = top 2 hits; page 2 = searchAfter(page 1's last FieldDoc, n = 6) must equal hits 2..5 of the
// full sorted result (IndexSearcher#searchAfter).
// Controls: (a) the same searchAfter with the cursor value divided by the planet's mean radius
// (meters -> planet units, what compareTop actually compares against) must page correctly;
// (b) on PlanetModel.SPHERE (mean radius 1.0, meters == planet units) the stock cursor must page
// correctly.
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

public class Geo3DSearchAfterHistoryProbe {
  static final int N = 6;
  static int bugs = 0, controlFails = 0, skipped = 0, earthChecks = 0;

  static String docs(ScoreDoc[] sds, int from) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = from; i < sds.length; i++) sb.append(sds[i].doc).append(i + 1 < sds.length ? "," : "");
    return sb.append("]").toString();
  }

  static String docsWithout(ScoreDoc[] sds, int skipDoc) {
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (ScoreDoc sd : sds) {
      if (sd.doc == skipDoc) continue;
      if (!first) sb.append(",");
      sb.append(sd.doc);
      first = false;
    }
    return sb.append("]").toString();
  }

  @SuppressWarnings("deprecation")
  static void check(String planetName, PlanetModel planet, String name, SortField sortField, boolean isControl)
      throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig((Analyzer) null));
    for (int i = 0; i < N; i++) {
      Document doc = new Document();
      doc.add(new Geo3DDocValuesField("p", new GeoPoint(planet, Math.toRadians(3 + i), 0), planet));
      w.addDocument(doc);
    }
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    try {
      IndexSearcher s = new IndexSearcher(r);
      MatchAllDocsQuery q = new MatchAllDocsQuery();
      Sort sort = new Sort(sortField);
      TopDocs full = s.search(q, N, sort);
      boolean ok = full.scoreDocs.length == N;
      double prev = Double.NEGATIVE_INFINITY;
      StringBuilder vals = new StringBuilder();
      for (ScoreDoc sd : full.scoreDocs) {
        double d = (Double) ((FieldDoc) sd).fields[0];
        vals.append(String.format("%.4f ", d));
        ok &= Double.isFinite(d) && d > prev;
        prev = d;
      }
      String tag = planetName + "/" + name + (isControl ? " (control)" : "");
      if (!ok) {
        System.out.println(tag + ": SKIP (vacuous setup) values(m)=" + vals);
        skipped++;
        return;
      }
      if (!isControl) earthChecks++;
      TopDocs page1 = s.search(q, 2, sort);
      FieldDoc last = (FieldDoc) page1.scoreDocs[1];
      TopDocs page2 = s.searchAfter(last, q, N, sort);
      String exp = docs(full.scoreDocs, 2), act = docs(page2.scoreDocs, 0);
      double top = (Double) last.fields[0];
      double meanRadius = planet.getMeanRadius();
      FieldDoc conv = new FieldDoc(last.doc, last.score, new Object[] {top / meanRadius});
      TopDocs page2c = s.searchAfter(conv, q, N, sort);
      String actc = docsWithout(page2c.scoreDocs, last.doc);
      boolean bug = !exp.equals(act);
      boolean ctlOk = exp.equals(actc);
      System.out.println(
          tag + ": " + (bug ? "BUG" : "ok")
              + " full=" + docs(full.scoreDocs, 0) + " values(m)=" + vals.toString().trim()
              + " cursor(m)=" + top
              + " page2 expected=" + exp + " actual=" + act
              + " | control cursor(planet units)=" + (top / meanRadius)
              + " page2=" + docs(page2c.scoreDocs, 0) + (ctlOk ? " CONTROL-OK" : " CONTROL-FAIL"));
      if (isControl) {
        if (bug || !ctlOk) controlFails++;
      } else {
        if (bug) bugs++;
        if (!ctlOk) controlFails++;
      }
    } finally {
      r.close();
    }
  }

  public static void main(String[] args) throws Exception {
    PlanetModel wgs = PlanetModel.WGS84;
    Polygon square = new Polygon(new double[] {-1, 1, 1, -1, -1}, new double[] {-1, -1, 1, 1, -1});
    check("WGS84", wgs, "newDistanceSort", Geo3DDocValuesField.newDistanceSort("p", 0, 0, 2_000_000, wgs), false);
    check("WGS84", wgs, "newPathSort",
        Geo3DDocValuesField.newPathSort("p", new double[] {0, 10}, new double[] {-1, -1}, 500_000, wgs), false);
    check("WGS84", wgs, "newOutsideDistanceSort",
        Geo3DDocValuesField.newOutsideDistanceSort("p", 0, 0, 200_000, wgs), false);
    check("WGS84", wgs, "newOutsideBoxSort", Geo3DDocValuesField.newOutsideBoxSort("p", -1, 1, -1, 1, wgs), false);
    check("WGS84", wgs, "newOutsidePathSort",
        Geo3DDocValuesField.newOutsidePathSort("p", new double[] {0, 10}, new double[] {-5, -5}, 100_000, wgs), false);
    check("WGS84", wgs, "newOutsidePolygonSort", Geo3DDocValuesField.newOutsidePolygonSort("p", wgs, square), false);
    check("WGS84", wgs, "newOutsideLargePolygonSort",
        Geo3DDocValuesField.newOutsideLargePolygonSort("p", wgs, square), false);
    PlanetModel clarke = PlanetModel.CLARKE_1866;
    check("CLARKE_1866", clarke, "newDistanceSort",
        Geo3DDocValuesField.newDistanceSort("p", 0, 0, 2_000_000, clarke), false);
    PlanetModel sphere = PlanetModel.SPHERE;
    check("SPHERE", sphere, "newDistanceSort", Geo3DDocValuesField.newDistanceSort("p", 0, 0, 0.5, sphere), true);
    System.out.println("summary: bug_reproduces_in=" + bugs + " of " + earthChecks
        + " Earth-model sorts, control_fails=" + controlFails + ", skipped=" + skipped);
    if (controlFails > 0) System.exit(3);
    if (bugs > 0) {
      System.out.println("FAIL: searchAfter lost hits");
      System.exit(1);
    }
    System.out.println("PASS");
  }
}
