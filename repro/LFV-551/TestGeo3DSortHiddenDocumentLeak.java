/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package geo3dsecprobe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterDirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * Security reach of the Geo3D comparator re-read: can a search that is restricted to some documents
 * get back a value computed from a document it must not see?
 *
 * <p>Public API only (separate package), default codec pinned, one segment, plain IndexSearcher.
 * Every test asserts the documented behaviour (Geo3DDocValuesField javadoc: "The value returned in
 * FieldDoc for the hits contains a Double instance with the distance in meters", and the closest of
 * the hit's own values is used), so a failure means the bug. The reference distance of a document is
 * the sort value Lucene returns for that document when it is the only document of a separate index.
 *
 * <p>Layout (docID order, one segment): tenant A's own documents, each followed by a tenant B
 * document that A must not see. Tenant A's first document has two values, which makes the field
 * multi-valued in the segment (any multi-valued document in the segment has the same effect).
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "probe prints its measurements")
public class TestGeo3DSortHiddenDocumentLeak extends LuceneTestCase {

  private static final String F = "p";

  // the constructor (deprecated on main in favour of INSTANCE) also compiles against 9.x releases
  @SuppressWarnings("deprecation")
  private static final Query MATCH_ALL = new MatchAllDocsQuery();

  /** Tenant A, two values far in the south-west: makes the field multi-valued in the segment. */
  private static final double[][] A_MULTI = {{-50, -100}, {-51, -100}};

  /** Tenant A, one value near the query centres. */
  private static final double[][] A_NEAR = {{1, 0}};

  /** Tenant B (hidden from A): the location an attacker in tenant A tries to learn. */
  private static final double[][] B_SECRET = {{12.3456, 7.6543}};

  /** Tenant B filler at the end, so no read runs past the last value of the segment. */
  private static final double[][] B_FILLER = {{20, 20}};

  /** Radius of the sort circle on the unit sphere (radians): every document above is inside it. */
  private static final double R_SPHERE = 2.5;

  private static Document doc(PlanetModel pm, String id, String tenant, double[][] latlons) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new StringField("tenant", tenant, Field.Store.YES));
    for (double[] ll : latlons) {
      d.add(
          new Geo3DDocValuesField(
              F, new GeoPoint(pm, Math.toRadians(ll[0]), Math.toRadians(ll[1])), pm));
    }
    return d;
  }

  private static IndexWriterConfig iwc() {
    IndexWriterConfig c = new IndexWriterConfig();
    c.setCodec(TestUtil.getDefaultCodec());
    c.setMergePolicy(NoMergePolicy.INSTANCE);
    return c;
  }

  /** One segment holding A_MULTI, A_NEAR, B_SECRET, B_FILLER in this docID order. */
  private static Directory standardIndex(PlanetModel pm) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, iwc())) {
      w.addDocument(doc(pm, "a-multi", "A", A_MULTI));
      w.addDocument(doc(pm, "a-near", "A", A_NEAR));
      w.addDocument(doc(pm, "b-secret", "B", B_SECRET));
      w.addDocument(doc(pm, "b-filler", "B", B_FILLER));
    }
    return dir;
  }

  /** Sort value Lucene returns for a document holding these points when it is alone in an index. */
  private static double reference(PlanetModel pm, double[][] latlons, SortField sf)
      throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc())) {
        w.addDocument(doc(pm, "ref", "R", latlons));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        TopDocs td = new IndexSearcher(r).search(MATCH_ALL, 1, new Sort(sf));
        return (Double) ((FieldDoc) td.scoreDocs[0]).fields[0];
      }
    }
  }

  private static Query tenantFilter(String tenant) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("tenant", tenant)), BooleanClause.Occur.FILTER)
        .build();
  }

  private static String id(IndexSearcher s, ScoreDoc sd) throws IOException {
    return s.storedFields().document(sd.doc).get("id");
  }

  private static SortField sphereSort(double latDeg, double lonDeg) {
    return Geo3DDocValuesField.newDistanceSort(F, latDeg, lonDeg, R_SPHERE, PlanetModel.SPHERE);
  }

  /** Top-1 hit for tenant A; returns {reported, ownReference, hiddenReference}. */
  private double[] topHitForTenantA(IndexSearcher s, PlanetModel pm, SortField sf, String label)
      throws IOException {
    TopDocs td = s.search(tenantFilter("A"), 1, new Sort(sf));
    ScoreDoc hit = td.scoreDocs[0];
    String hitId = id(s, hit);
    double reported = (Double) ((FieldDoc) hit).fields[0];
    double own = reference(pm, "a-near".equals(hitId) ? A_NEAR : A_MULTI, sf);
    double hidden = reference(pm, B_SECRET, sf);
    System.out.println(
        String.format(
            Locale.ROOT,
            "%s: hit=%s reported=%.17g own=%.17g hidden(b-secret)=%.17g reported==hidden:%s",
            label,
            hitId,
            reported,
            own,
            hidden,
            Double.compare(reported, hidden) == 0));
    assertEquals("tenant A sees only tenant A documents", "A", s.storedFields().document(hit.doc).get("tenant"));
    return new double[] {reported, own, hidden};
  }

  /** Filter-based restriction (a FILTER clause on tenant): the hidden document's distance leaks. */
  public void testFilteredOutDocumentDistanceIsReturnedAsHitSortValue() throws Exception {
    try (Directory dir = standardIndex(PlanetModel.SPHERE);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      double[] v = topHitForTenantA(s, PlanetModel.SPHERE, sphereSort(0, 0), "filter/SPHERE");
      assertEquals(
          "sort value of tenant A's hit must be its own distance, not tenant B's "
              + v[2],
          v[1],
          v[0],
          1e-12);
    }
  }

  /**
   * Three centres chosen by the searcher: the three returned sort values are arc distances on the
   * unit sphere, so the hidden point p solves c_i . p = cos(d_i). The hidden location must not be
   * recoverable from tenant A's results.
   */
  public void testHiddenLocationRecoveredFromThreeSearches() throws Exception {
    double[][] centres = {{0, 0}, {0, 30}, {30, 0}};
    double[][] m = new double[3][];
    double[] rhs = new double[3];
    try (Directory dir = standardIndex(PlanetModel.SPHERE);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      for (int i = 0; i < 3; i++) {
        double[] v =
            topHitForTenantA(
                s,
                PlanetModel.SPHERE,
                sphereSort(centres[i][0], centres[i][1]),
                "trilateration centre " + i);
        double lat = Math.toRadians(centres[i][0]);
        double lon = Math.toRadians(centres[i][1]);
        m[i] =
            new double[] {Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
        rhs[i] = Math.cos(v[0]); // SPHERE mean radius is 1: the value is the arc in radians
      }
    }
    double[] p = solve3(m, rhs);
    double norm = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
    double latDeg = Math.toDegrees(Math.asin(p[2] / norm));
    double lonDeg = Math.toDegrees(Math.atan2(p[1], p[0]));
    double errDeg =
        Math.max(Math.abs(latDeg - B_SECRET[0][0]), Math.abs(lonDeg - B_SECRET[0][1]));
    System.out.println(
        String.format(
            Locale.ROOT,
            "recovered from tenant A's sort values: lat=%.6f lon=%.6f; b-secret is at lat=%.6f"
                + " lon=%.6f; max error %.2e degrees",
            latDeg,
            lonDeg,
            B_SECRET[0][0],
            B_SECRET[0][1],
            errDeg));
    assertTrue(
        String.format(
            Locale.ROOT,
            "tenant B's hidden location (%.4f, %.4f) was recovered from tenant A's results as"
                + " (%.6f, %.6f)",
            B_SECRET[0][0],
            B_SECRET[0][1],
            latDeg,
            lonDeg),
        errDeg > 1e-3);
  }

  /** A deleted document's distance is returned as the sort value of a live hit. */
  public void testDeletedDocumentDistanceIsReturnedAsHitSortValue() throws Exception {
    PlanetModel pm = PlanetModel.SPHERE;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc())) {
        w.addDocument(doc(pm, "a-multi", "A", A_MULTI));
        w.addDocument(doc(pm, "a-near", "A", A_NEAR));
        w.addDocument(doc(pm, "b-secret", "A", B_SECRET)); // same tenant, then deleted
        w.addDocument(doc(pm, "b-filler", "A", B_FILLER));
        w.commit();
        w.deleteDocuments(new Term("id", "b-secret"));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("one segment", 1, r.leaves().size());
        assertEquals("b-secret is deleted", 1, r.numDeletedDocs());
        IndexSearcher s = new IndexSearcher(r);
        SortField sf = sphereSort(0, 0);
        TopDocs td = s.search(MATCH_ALL, 1, new Sort(sf));
        String hitId = id(s, td.scoreDocs[0]);
        double reported = (Double) ((FieldDoc) td.scoreDocs[0]).fields[0];
        double own = reference(pm, A_NEAR, sf);
        double deleted = reference(pm, B_SECRET, sf);
        System.out.println(
            String.format(
                Locale.ROOT,
                "deleted/SPHERE: hit=%s reported=%.17g own=%.17g deleted(b-secret)=%.17g"
                    + " reported==deleted:%s",
                hitId,
                reported,
                own,
                deleted,
                Double.compare(reported, deleted) == 0));
        assertEquals("a-near", hitId);
        assertEquals("live hit must report its own distance, not the deleted document's " + deleted, own, reported, 1e-12);
      }
    }
  }

  /**
   * Reader-wrapper restriction: a FilterDirectoryReader hides tenant B through getLiveDocs, the
   * usual shape of a document-level security layer. Doc values of hidden documents stay readable
   * through the wrapped reader, and the comparator reads them.
   */
  public void testLiveDocsHidingReaderWrapperLeaksHiddenDistance() throws Exception {
    try (Directory dir = standardIndex(PlanetModel.SPHERE);
        DirectoryReader raw = DirectoryReader.open(dir);
        DirectoryReader r = new HideTenantReader(raw, "B")) {
      assertEquals("tenant B hidden", 2, r.numDocs());
      IndexSearcher s = new IndexSearcher(r);
      SortField sf = sphereSort(0, 0);
      TopDocs td = s.search(MATCH_ALL, 1, new Sort(sf));
      String hitId = id(s, td.scoreDocs[0]);
      double reported = (Double) ((FieldDoc) td.scoreDocs[0]).fields[0];
      double own = reference(PlanetModel.SPHERE, A_NEAR, sf);
      double hidden = reference(PlanetModel.SPHERE, B_SECRET, sf);
      System.out.println(
          String.format(
              Locale.ROOT,
              "liveDocs-wrapper/SPHERE: hit=%s reported=%.17g own=%.17g hidden(b-secret)=%.17g"
                  + " reported==hidden:%s",
              hitId,
              reported,
              own,
              hidden,
              Double.compare(reported, hidden) == 0));
      assertEquals("a-near", hitId);
      assertEquals("hit must report its own distance, not the hidden document's " + hidden, own, reported, 1e-12);
    }
  }

  /**
   * searchAfter (PagingFieldCollector calls compareTop and then copy for every hit): with a page
   * larger than the hit count, every one of tenant A's single-valued hits reports the distance of
   * the tenant B document stored right after it. No full queue is needed.
   */
  public void testSearchAfterEveryHitReportsTheNextHiddenDocument() throws Exception {
    PlanetModel pm = PlanetModel.SPHERE;
    double[][][] aDocs = {{{1, 0}}, {{2, 0}}, {{3, 0}}};
    double[][][] bDocs = {{{12.3456, 7.6543}}, {{-8.5, 33.25}}, {{40.125, -21.5}}};
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc())) {
        w.addDocument(doc(pm, "a-multi", "A", A_MULTI));
        for (int i = 0; i < 3; i++) {
          w.addDocument(doc(pm, "a" + i, "A", aDocs[i]));
          w.addDocument(doc(pm, "b" + i, "B", bDocs[i]));
        }
        w.addDocument(doc(pm, "b-filler", "B", B_FILLER));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        SortField sf = sphereSort(0, 0);
        // a client-supplied cursor: "after distance 0" (SPHERE, so no unit mismatch in compareTop)
        FieldDoc after = new FieldDoc(0, Float.NaN, new Object[] {0.0});
        TopDocs td = s.searchAfter(after, tenantFilter("A"), 10, new Sort(sf));
        int leaked = 0;
        StringBuilder sb = new StringBuilder();
        for (ScoreDoc sd : td.scoreDocs) {
          String hitId = id(s, sd);
          if (hitId.equals("a-multi")) {
            continue;
          }
          int i = hitId.charAt(1) - '0';
          double reported = (Double) ((FieldDoc) sd).fields[0];
          double own = reference(pm, aDocs[i], sf);
          double next = reference(pm, bDocs[i], sf);
          boolean isNext = Double.compare(reported, next) == 0;
          if (isNext) {
            leaked++;
          }
          sb.append(
              String.format(
                  Locale.ROOT,
                  "%n  %s reported=%.17g own=%.17g hidden b%d=%.17g reported==hidden:%s",
                  hitId,
                  reported,
                  own,
                  i,
                  next,
                  isNext));
        }
        System.out.println("searchAfter/SPHERE:" + sb);
        assertEquals("hits reporting a hidden document's distance:" + sb, 0, leaked);
      }
    }
  }

  /** Same as the filter test on WGS84: the leaked value is the hidden document's distance in m. */
  public void testFilteredOutDocumentDistanceWgs84Meters() throws Exception {
    try (Directory dir = standardIndex(PlanetModel.WGS84);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      SortField sf =
          Geo3DDocValuesField.newDistanceSort(F, 0, 0, 15_000_000, PlanetModel.WGS84);
      double[] v = topHitForTenantA(s, PlanetModel.WGS84, sf, "filter/WGS84");
      assertEquals(
          "sort value (m) of tenant A's hit must be its own distance, not tenant B's " + v[2],
          v[1],
          v[0],
          1e-6);
    }
  }

  /** The outside-distance comparator has the same re-read. */
  public void testOutsideDistanceSortLeaksHiddenDistance() throws Exception {
    try (Directory dir = standardIndex(PlanetModel.SPHERE);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      SortField sf =
          Geo3DDocValuesField.newOutsideDistanceSort(
              F, 0, 0, Math.toRadians(0.1), PlanetModel.SPHERE);
      double[] v = topHitForTenantA(s, PlanetModel.SPHERE, sf, "outside-filter/SPHERE");
      assertEquals(
          "outside distance of tenant A's hit must be its own, not tenant B's " + v[2],
          v[1],
          v[0],
          1e-12);
    }
  }

  /** Gaussian elimination with partial pivoting, 3x3. */
  private static double[] solve3(double[][] a0, double[] b0) {
    double[][] a = new double[3][4];
    for (int i = 0; i < 3; i++) {
      System.arraycopy(a0[i], 0, a[i], 0, 3);
      a[i][3] = b0[i];
    }
    for (int c = 0; c < 3; c++) {
      int piv = c;
      for (int i = c + 1; i < 3; i++) {
        if (Math.abs(a[i][c]) > Math.abs(a[piv][c])) {
          piv = i;
        }
      }
      double[] t = a[c];
      a[c] = a[piv];
      a[piv] = t;
      for (int i = 0; i < 3; i++) {
        if (i != c) {
          double f = a[i][c] / a[c][c];
          for (int k = c; k < 4; k++) {
            a[i][k] -= f * a[c][k];
          }
        }
      }
    }
    return new double[] {a[0][3] / a[0][0], a[1][3] / a[1][1], a[2][3] / a[2][2]};
  }

  /** Hides every document of one tenant through getLiveDocs (document-level-security shape). */
  static final class HideTenantReader extends FilterDirectoryReader {
    private final String hidden;

    HideTenantReader(DirectoryReader in, String hidden) throws IOException {
      super(in, new HideTenantWrapper(hidden));
      this.hidden = hidden;
    }

    @Override
    protected DirectoryReader doWrapDirectoryReader(DirectoryReader in) throws IOException {
      return new HideTenantReader(in, hidden);
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }

  static final class HideTenantWrapper extends FilterDirectoryReader.SubReaderWrapper {
    private final String hidden;

    HideTenantWrapper(String hidden) {
      this.hidden = hidden;
    }

    @Override
    public LeafReader wrap(LeafReader reader) {
      try {
        return new HideTenantLeaf(reader, hidden);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  static final class HideTenantLeaf extends FilterLeafReader {
    private final FixedBitSet live;
    private final int numDocs;

    HideTenantLeaf(LeafReader in, String hidden) throws IOException {
      super(in);
      live = new FixedBitSet(in.maxDoc());
      live.set(0, in.maxDoc());
      Bits inLive = in.getLiveDocs();
      if (inLive != null) {
        for (int d = 0; d < in.maxDoc(); d++) {
          if (inLive.get(d) == false) {
            live.clear(d);
          }
        }
      }
      PostingsEnum pe = in.postings(new Term("tenant", hidden));
      if (pe != null) {
        for (int d = pe.nextDoc(); d != PostingsEnum.NO_MORE_DOCS; d = pe.nextDoc()) {
          live.clear(d);
        }
      }
      numDocs = live.cardinality();
    }

    @Override
    public Bits getLiveDocs() {
      return live;
    }

    @Override
    public int numDocs() {
      return numDocs;
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return null;
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }
}
