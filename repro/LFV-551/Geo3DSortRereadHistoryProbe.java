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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * History probe (main(), no test framework) for: Geo3D distance sorts read a document's
 * SortedNumericDocValues twice (compareBottom, then copy) once the top-N queue is full.
 *
 * <p>Runs unchanged against every release that has Geo3DDocValuesField sorts (6.1.0 and later):
 * the PlanetModel-taking constructor and sort factories (8.5.0 and later) are looked up by
 * reflection on public API, falling back to the older WGS84-only signatures.
 *
 * <p>Oracle (metamorphic, plus a fixed expected order): the javadoc promises that "if a document
 * contains multiple values for the field, the closest distance ... is used". Documents lie due
 * north of the centre (lat 0, lon 0) at the listed latitudes (degrees), one segment. The full
 * sort (numHits = number of documents) must be the expected order, and the top-2 search must be
 * its head. A control layout with only single-valued documents must pass on every version.
 *
 * <p>Exit 0: not affected (all cases and the control pass). Exit 1: affected (some bug case fails,
 * the control passes). Exit 2: inconclusive (the control fails).
 */
public class Geo3DSortRereadHistoryProbe {

  static final PlanetModel PM = PlanetModel.WGS84;

  /** Multi-valued document 2 is the nearest (0.5 degrees). */
  static final double[][] MULTI = {{1}, {2}, {9, 0.5}, {5}, {6}};

  static final int[] MULTI_ORDER = {2, 0, 1, 3, 4};

  /** Single-valued document 2 in a segment where document 4 has two values. */
  static final double[][] MIXED = {{1}, {2}, {0.5}, {5}, {6, 7}};

  static final int[] MIXED_ORDER = {2, 0, 1, 3, 4};

  /** The multi-valued document is the last one with values. */
  static final double[][] LAST = {{1}, {2}, {9, 0.5}};

  static final int[] LAST_ORDER = {2, 0, 1};

  /** Control: every document has one value. */
  static final double[][] SINGLE = {{1}, {2}, {0.5}, {5}, {6}};

  static final int[] SINGLE_ORDER = {2, 0, 1, 3, 4};

  static final Analyzer NO_TEXT =
      new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
          throw new UnsupportedOperationException("no text fields in this probe");
        }
      };

  static IndexableField point(double latDegrees) throws Exception {
    GeoPoint p = new GeoPoint(PM, Math.toRadians(latDegrees), 0);
    try {
      return Geo3DDocValuesField.class
          .getConstructor(String.class, GeoPoint.class, PlanetModel.class)
          .newInstance("p", p, PM);
    } catch (NoSuchMethodException e) {
      return Geo3DDocValuesField.class
          .getConstructor(String.class, GeoPoint.class)
          .newInstance("p", p);
    }
  }

  static SortField sort(String factory, double radiusMeters) throws Exception {
    Method m;
    Object[] args;
    try {
      m =
          Geo3DDocValuesField.class.getMethod(
              factory, String.class, double.class, double.class, double.class, PlanetModel.class);
      args = new Object[] {"p", 0d, 0d, radiusMeters, PM};
    } catch (NoSuchMethodException e) {
      m =
          Geo3DDocValuesField.class.getMethod(
              factory, String.class, double.class, double.class, double.class);
      args = new Object[] {"p", 0d, 0d, radiusMeters};
    }
    return (SortField) m.invoke(null, args);
  }

  static Directory newDirectory(String kind, Path base) throws Exception {
    if (kind.equals("fs")) {
      Path p = Files.createTempDirectory(base, "idx");
      try {
        return FSDirectory.open(p);
      } catch (LinkageError e) {
        // 6.1/6.2 MMapDirectory cannot initialise its unmap hack on a modern JDK: use NIOFSDirectory
        return new NIOFSDirectory(p);
      }
    }
    for (String cn :
        new String[] {
          "org.apache.lucene.store.ByteBuffersDirectory", "org.apache.lucene.store.RAMDirectory"
        }) {
      try {
        return (Directory) Class.forName(cn).getConstructor().newInstance();
      } catch (ClassNotFoundException e) {
        // try the next one
      }
    }
    throw new IllegalStateException("no heap directory");
  }

  static String docs(ScoreDoc[] hits) {
    int[] out = new int[hits.length];
    for (int i = 0; i < hits.length; i++) {
      out[i] = hits[i].doc;
    }
    return Arrays.toString(out);
  }

  static String run(String dirKind, Path base, SortField sf, double[][] lats, int[] order)
      throws Exception {
    try (Directory dir = newDirectory(dirKind, base)) {
      String dirName = " dir=" + dir.getClass().getSimpleName();
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(NO_TEXT));
      for (double[] docLats : lats) {
        Document doc = new Document();
        for (double lat : docLats) {
          doc.add(point(lat));
        }
        w.addDocument(doc);
      }
      w.close();
      DirectoryReader r = DirectoryReader.open(dir);
      try {
        IndexSearcher s = new IndexSearcher(r);
        Sort sort = new Sort(sf);
        Query q = new MatchAllDocsQuery();
        String expFull = Arrays.toString(order);
        String expTop2 = Arrays.toString(Arrays.copyOf(order, 2));
        String full;
        try {
          full = docs(s.search(q, lats.length, sort).scoreDocs);
        } catch (Exception e) {
          return "EXC(full) " + describe(e) + dirName;
        }
        TopDocs top2;
        try {
          top2 = s.search(q, 2, sort);
        } catch (Exception e) {
          return "EXC(top2) " + describe(e) + dirName;
        }
        String t2 = docs(top2.scoreDocs);
        String vals =
            ((FieldDoc) top2.scoreDocs[0]).fields[0] + "," + ((FieldDoc) top2.scoreDocs[1]).fields[0];
        if (full.equals(expFull) && t2.equals(expTop2)) {
          return "ok top2=" + t2 + " values=" + vals + " segments=" + r.leaves().size() + dirName;
        }
        return "WRONG full=" + full + " (expected " + expFull + ") top2=" + t2 + " (expected "
            + expTop2 + ") values=" + vals + " segments=" + r.leaves().size() + dirName;
      } finally {
        r.close();
      }
    }
  }

  static String describe(Throwable e) {
    StringBuilder sb = new StringBuilder(e.getClass().getSimpleName() + ": " + redact(e.getMessage()));
    for (Throwable c = e.getCause(); c != null; c = c.getCause()) {
      sb.append(" <- ").append(c.getClass().getSimpleName()).append(": ").append(redact(c.getMessage()));
    }
    return sb.toString();
  }

  /** Drops the temporary index path from exception messages. */
  static String redact(String msg) {
    return msg == null ? null : msg.replaceAll("path=\"[^\"]*[/\\\\]", "path=\"<tmp>/");
  }

  public static void main(String[] args) throws Exception {
    Path base = Files.createTempDirectory("geo3dhist");
    String version = org.apache.lucene.util.Version.LATEST.toString();
    System.out.println("lucene Version.LATEST=" + version);
    boolean newApi;
    try {
      Geo3DDocValuesField.class.getConstructor(String.class, GeoPoint.class, PlanetModel.class);
      newApi = true;
    } catch (NoSuchMethodException e) {
      newApi = false;
    }
    System.out.println("api=" + (newApi ? "planet-model-arg (8.5+)" : "wgs84-only (<8.5)"));

    String[][] sorts = {
      {"newDistanceSort", "2000000"}, // about 18 degrees: every point is inside the circle
      {"newOutsideDistanceSort", "20000"} // about 0.18 degrees: every point is outside the circle
    };
    List<String> bugLines = new ArrayList<>();
    List<String> ctrlLines = new ArrayList<>();
    boolean affected = false;
    boolean controlOk = true;
    for (String[] so : sorts) {
      for (String dirKind : new String[] {"fs", "heap"}) {
        SortField sf;
        try {
          sf = sort(so[0], Double.parseDouble(so[1]));
        } catch (InvocationTargetException e) {
          throw new RuntimeException(e.getCause());
        }
        String tag = so[0] + "/" + dirKind;
        String c = run(dirKind, base, sf, SINGLE, SINGLE_ORDER);
        ctrlLines.add("CTRL single-valued " + tag + ": " + c);
        if (!c.startsWith("ok")) {
          controlOk = false;
        }
        String[] names = {"A multi-valued-nearest", "C single-in-mixed-segment", "D last-doc-multi"};
        double[][][] layouts = {MULTI, MIXED, LAST};
        int[][] orders = {MULTI_ORDER, MIXED_ORDER, LAST_ORDER};
        for (int i = 0; i < names.length; i++) {
          String res = run(dirKind, base, sf, layouts[i], orders[i]);
          bugLines.add(names[i] + " " + tag + ": " + res);
          if (!res.startsWith("ok")) {
            affected = true;
          }
        }
      }
    }
    for (String l : ctrlLines) {
      System.out.println(l);
    }
    for (String l : bugLines) {
      System.out.println(l);
    }
    if (!controlOk) {
      System.out.println("VERDICT: inconclusive (control failed)");
      System.exit(2);
    }
    if (affected) {
      System.out.println("VERDICT: affected");
      System.exit(1);
    }
    System.out.println("VERDICT: not affected");
  }
}
