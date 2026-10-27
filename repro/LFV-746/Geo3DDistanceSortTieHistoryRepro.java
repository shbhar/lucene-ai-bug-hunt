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
package hist;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Version-portable repro for the history sweep of the Geo3D distance-sort tie bug.
 *
 * <p>It runs on every release that has Geo3DDocValuesField.newDistanceSort (6.1.0 and later) and
 * on main. The two public signatures that changed over time are chosen by reflection: up to 8.4
 * the field constructor and newDistanceSort take no PlanetModel (WGS84 is implied); from 8.5 on
 * they take one. Everything else (IndexWriter, NIOFSDirectory, NumericDocValuesField,
 * IndexSearcher.search(Query, int, Sort), FieldDoc) has the same signature in all of them. No
 * LuceneTestCase, so no test-framework jar is needed.
 *
 * <p>Oracle: search(q, n, sort) must return the first n hits of search(q, 100, sort) (a top-n
 * search returns the prefix of the full sorted order; the second SortField breaks ties of the
 * first). Documents are identified by their "tie" value (FieldDoc.fields[1]); the index is one
 * segment in insertion order.
 *
 * <p>Exit codes: 0 bug absent (every scenario matches); 1 bug present (a bug scenario differs and
 * all controls match); 2 a control differs (inconclusive); 5 the API is absent at run time.
 */
public class Geo3DDistanceSortTieHistoryRepro {

  static final double MAX_RADIUS_METERS = 1_000_000; // 1,000 km around (0, 0)

  public static void main(String[] args) throws Exception {
    Method sortFactory;
    try {
      sortFactory = distanceSortFactory();
    } catch (NoSuchMethodException e) {
      System.out.println("API_ABSENT: no Geo3DDocValuesField.newDistanceSort(field, lat, lon, radius[, PlanetModel])");
      System.exit(5);
      return;
    }
    System.out.println("newDistanceSort signature: " + sortFactory);

    boolean bug = false;
    boolean controlFailed = false;

    // Bug scenario 1: five documents, all at latitude 60 (about 6,700 km away, far outside the
    // radius), so every one has distance +Infinity and the second SortField must decide.
    bug |= !scenario("outside-only", 2, new double[][] {far(), far(), far(), far(), far()});

    // Bug scenario 2: the same plus a sixth document without the field (also sorts last).
    bug |= !scenario("outside-plus-missing", 2, new double[][] {far(), far(), far(), far(), far(), null});

    // Control 1: one document inside the radius, five without the field (tie on +Infinity
    // through the documented "missing" path).
    controlFailed |= !scenario("control-missing-only", 3, new double[][] {near(), null, null, null, null, null});

    // Control 2: five documents at the same point inside the radius (tie on a finite distance).
    controlFailed |= !scenario("control-inside-tie", 2, new double[][] {near(), near(), near(), near(), near()});

    if (controlFailed) {
      System.out.println("RESULT: INCONCLUSIVE (a control scenario differs)");
      System.exit(2);
    }
    if (bug) {
      System.out.println("RESULT: BUG PRESENT (top-n is not the prefix of the full sorted order)");
      System.exit(1);
    }
    System.out.println("RESULT: BUG ABSENT");
  }

  static double[] far() {
    return new double[] {60, 0};
  }

  static double[] near() {
    return new double[] {0, 1}; // about 111 km from (0, 0), inside the radius
  }

  /** Indexes docs[i] (lat, lon in degrees, or null for no location) with tie=i; true if top-n == prefix. */
  static boolean scenario(String name, int n, double[][] docs) throws Exception {
    Path tmp = Files.createTempDirectory("geo3d-sort-tie-");
    try (Directory dir = new NIOFSDirectory(tmp)) {
      IndexWriterConfig iwc = new IndexWriterConfig(new NoTextAnalyzer());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < docs.length; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("tie", i));
          if (docs[i] != null) {
            doc.add(locationField("loc", docs[i][0], docs[i][1]));
          }
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort =
            new Sort(
                newDistanceSort("loc", 0, 0, MAX_RADIUS_METERS),
                new SortField("tie", SortField.Type.LONG, true)); // ties: highest "tie" first
        List<String> full = hits(searcher.search(new MatchAllDocsQuery(), 100, sort));
        List<String> top = hits(searcher.search(new MatchAllDocsQuery(), n, sort));
        List<String> prefix = full.subList(0, Math.min(n, full.size()));
        boolean ok = prefix.equals(top);
        System.out.println(
            name + ": segments=" + reader.leaves().size() + " full=" + full + " top" + n + "=" + top
                + " expected top" + n + "=" + prefix + (ok ? " OK" : " MISMATCH"));
        return ok;
      }
    } finally {
      try (Stream<Path> s = Files.walk(tmp)) {
        s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }

  static List<String> hits(TopFieldDocs td) {
    List<String> out = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) {
      FieldDoc fd = (FieldDoc) sd;
      out.add("tie" + fd.fields[1] + "=" + fd.fields[0]);
    }
    return out;
  }

  // ---- signature differences across releases (chosen by reflection) ----

  static Method distanceSortFactory() throws NoSuchMethodException {
    try { // 8.5.0 and later
      return Geo3DDocValuesField.class.getMethod(
          "newDistanceSort", String.class, double.class, double.class, double.class, PlanetModel.class);
    } catch (NoSuchMethodException e) { // 6.1.0 to 8.4.x: WGS84 implied
      return Geo3DDocValuesField.class.getMethod(
          "newDistanceSort", String.class, double.class, double.class, double.class);
    }
  }

  static SortField newDistanceSort(String field, double lat, double lon, double radiusMeters)
      throws Exception {
    Method m = distanceSortFactory();
    try {
      if (m.getParameterCount() == 5) {
        return (SortField) m.invoke(null, field, lat, lon, radiusMeters, PlanetModel.WGS84);
      }
      return (SortField) m.invoke(null, field, lat, lon, radiusMeters);
    } catch (InvocationTargetException e) {
      throw (Exception) e.getCause();
    }
  }

  static Field locationField(String name, double latDeg, double lonDeg) throws Exception {
    GeoPoint p = new GeoPoint(PlanetModel.WGS84, Math.toRadians(latDeg), Math.toRadians(lonDeg));
    try { // 8.5.0 and later
      return Geo3DDocValuesField.class
          .getConstructor(String.class, GeoPoint.class, PlanetModel.class)
          .newInstance(name, p, PlanetModel.WGS84);
    } catch (NoSuchMethodException e) { // 6.1.0 to 8.4.x
      return Geo3DDocValuesField.class.getConstructor(String.class, GeoPoint.class).newInstance(name, p);
    }
  }

  /** Only doc-values fields are indexed, so the analyzer is never asked for components. */
  static final class NoTextAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      throw new UnsupportedOperationException("no tokenized fields in this repro");
    }
  }
}
