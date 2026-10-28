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
package dqhistory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;

/**
 * Version-portable main() probe for Lucene 4.0 to main (IndexWriterConfig API) for
 * "IndexWriter.deleteDocuments(Query) resolves the query on each segment on its own". Same
 * method as the 2.x/3.x probe: the reference is IndexSearcher.search(query) on the same
 * two-segment index; the probe also searches each segment's documents as a separate one-segment
 * index and reports whether the deleted set is the union of those per-segment results. A
 * one-segment control repeats the comparison with the same documents in a single segment: there
 * the delete must equal the search, so the only variable is the segment layout.
 *
 * <ul>
 *   <li>control(term): TermQuery. Must match in every version.
 *   <li>fuzzy-default: FuzzyQuery(Term), default maxExpansions 50 since 4.0. Segment A: 50 terms
 *       at edit distance 1 from "lucenex"; segment B: 10 terms at edit distance 2.
 *   <li>common-terms: CommonTermsQuery(SHOULD, SHOULD, 0.5) on "common", "rare" (4.1+).
 *   <li>mlt: MoreLikeThisQuery("zeta"), default minDocFreq 5; 3 of 6 documents per segment.
 *   <li>knn: k=2 nearest neighbours (KnnVectorQuery 9.0-9.x, KnnFloatVectorQuery 9.5+), 3
 *       vectors per segment.
 * </ul>
 *
 * <p>Only public API. Reflection only picks what the running release has (IndexWriterConfig with
 * or without Version, ByteBuffersDirectory or RAMDirectory, StandardAnalyzer constructor,
 * storedFields() or doc(int), the kNN classes, CommonTermsQuery).
 *
 * <p>Exit 0 when every check deletes exactly the searched documents, 1 when a check deviates, 3
 * when the control or a precondition (two segments, expected search hits) fails.
 */
public class ProbeDeleteQueryPerSegment4x {

  interface QueryMaker {
    Query make() throws Exception;
  }

  interface DocMaker {
    void addFields(Document doc, int idInSeg, int globalId) throws Exception;
  }

  static int deviating = 0;
  static boolean probeProblem = false;

  public static void main(String[] args) throws Exception {
    // control
    run(
        "control(term)",
        new int[] {3, 3},
        (doc, i, id) -> doc.add(new StringField("color", i == 0 ? "red" : "blue", Field.Store.NO)),
        () -> new TermQuery(new Term("color", "red")),
        2,
        true);

    // fuzzy-default
    final List<String> near = variants(1, 50);
    final List<String> far = variants(2, 10);
    run(
        "fuzzy-default(maxExpansions=50)",
        new int[] {50, 10},
        (doc, i, id) ->
            doc.add(
                new StringField("name", id < 50 ? near.get(id) : far.get(id - 50), Field.Store.NO)),
        () -> new FuzzyQuery(new Term("name", "lucenex")),
        50,
        false);

    // common terms (4.1+)
    final Class<?> ctq = classOrNull("org.apache.lucene.queries.CommonTermsQuery");
    if (ctq == null) {
      System.out.println("CHECK common-terms: SKIPPED (no CommonTermsQuery in this version)");
    } else {
      final String[] text = {"common rare", "common", "common", "common", "common", "rare", "x", "x"};
      run(
          "common-terms",
          new int[] {4, 4},
          (doc, i, id) -> doc.add(new TextField("t", text[id], Field.Store.NO)),
          () -> {
            Object q =
                ctq.getConstructor(BooleanClause.Occur.class, BooleanClause.Occur.class, float.class)
                    .newInstance(BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD, 0.5f);
            Method add = ctq.getMethod("add", Term.class);
            add.invoke(q, new Term("t", "common"));
            add.invoke(q, new Term("t", "rare"));
            return (Query) q;
          },
          2,
          false);
    }

    // more like this
    final Class<?> mlt = classOrNull("org.apache.lucene.queries.mlt.MoreLikeThisQuery");
    if (mlt == null) {
      System.out.println("CHECK mlt: SKIPPED (no MoreLikeThisQuery on the classpath)");
    } else {
      run(
          "mlt(minDocFreq=5)",
          new int[] {6, 6},
          (doc, i, id) -> doc.add(new TextField("body", i < 3 ? "zeta" : "other", Field.Store.NO)),
          () ->
              (Query)
                  mlt.getConstructor(String.class, String[].class, Analyzer.class, String.class)
                      .newInstance("zeta", new String[] {"body"}, analyzer(), "body"),
          6,
          false);
    }

    // kNN (9.0+)
    final Class<?> vsf = classOrNull("org.apache.lucene.index.VectorSimilarityFunction");
    Class<?> fieldClass = classOrNull("org.apache.lucene.document.KnnFloatVectorField");
    Class<?> queryClass = classOrNull("org.apache.lucene.search.KnnFloatVectorQuery");
    if (fieldClass == null || queryClass == null) {
      fieldClass = classOrNull("org.apache.lucene.document.KnnVectorField");
      queryClass = classOrNull("org.apache.lucene.search.KnnVectorQuery");
    }
    if (vsf == null || fieldClass == null || queryClass == null) {
      System.out.println("CHECK knn: SKIPPED (no vector search in this version)");
    } else {
      final Class<?> fc = fieldClass;
      final Class<?> qc = queryClass;
      final Object euclidean = vsf.getField("EUCLIDEAN").get(null);
      run(
          "knn(k=2," + qc.getSimpleName() + ")",
          new int[] {3, 3},
          (doc, i, id) ->
              doc.add(
                  (IndexableField)
                      fc.getConstructor(String.class, float[].class, vsf)
                          .newInstance("vector", new float[] {id + 1, 1f}, euclidean)),
          () ->
              (Query)
                  qc.getConstructor(String.class, float[].class, int.class)
                      .newInstance("vector", new float[] {0f, 1f}, 2),
          2,
          false);
    }

    System.out.println(
        "SUMMARY deviating_checks=" + deviating + " probe_problem=" + probeProblem);
    if (probeProblem) System.exit(3);
    if (deviating > 0) System.exit(1);
  }

  static void run(
      String name, int[] segSizes, DocMaker dm, QueryMaker qm, int expectedHits, boolean control)
      throws Exception {
    int r = check(name, segSizes, dm, qm, expectedHits, control);
    if (r == 1) deviating++;
    if (r == 3) probeProblem = true;
  }

  /** Returns 0 = deleted set equals searched set, 1 = deviates, 3 = probe problem. */
  static int check(
      String name, int[] segSizes, DocMaker dm, QueryMaker qm, int expectedHits, boolean isControl)
      throws Exception {
    Directory dir = build(segSizes, 0, segSizes.length, dm);
    int segCount = segmentCount(dir);
    Set<String> before = ids(dir, new MatchAllDocsQuery());
    Set<String> searched = ids(dir, qm.make());
    Set<String> perSegUnion = new TreeSet<>();
    for (int s = 0; s < segSizes.length; s++) {
      Directory d1 = build(segSizes, s, s + 1, dm);
      perSegUnion.addAll(ids(d1, qm.make()));
      d1.close();
    }
    IndexWriter w = new IndexWriter(dir, config());
    w.deleteDocuments(qm.make());
    w.commit();
    w.close();
    Set<String> after = ids(dir, new MatchAllDocsQuery());
    Set<String> deleted = new TreeSet<>(before);
    deleted.removeAll(after);
    int segCountAfter = segmentCount(dir);
    dir.close();

    Set<String> over = new TreeSet<>(deleted);
    over.removeAll(searched);
    Set<String> under = new TreeSet<>(searched);
    under.removeAll(deleted);
    boolean same = over.isEmpty() && under.isEmpty();
    System.out.println(
        "CHECK "
            + name
            + ": segments="
            + segCount
            + "->"
            + segCountAfter
            + " docs="
            + before.size()
            + " searched="
            + searched.size()
            + " deleted="
            + deleted.size()
            + " perSegmentUnion="
            + perSegUnion.size()
            + " deletedEqualsPerSegmentUnion="
            + deleted.equals(perSegUnion)
            + " overDeleted="
            + over.size()
            + " underDeleted="
            + under.size()
            + " -> "
            + (same ? "MATCHES search" : "DEVIATES from search"));

    // one-segment control: the same documents in a single segment; delete must equal search
    Directory sd = build(segSizes, 0, segSizes.length, dm, true);
    int sdSegs = segmentCount(sd);
    Set<String> sdBefore = ids(sd, new MatchAllDocsQuery());
    Set<String> sdSearched = ids(sd, qm.make());
    IndexWriter sw = new IndexWriter(sd, config());
    sw.deleteDocuments(qm.make());
    sw.commit();
    sw.close();
    Set<String> sdDeleted = new TreeSet<>(sdBefore);
    sdDeleted.removeAll(ids(sd, new MatchAllDocsQuery()));
    sd.close();
    boolean sdSame = sdDeleted.equals(sdSearched);
    System.out.println(
        "  one-segment layout of the same documents: segments="
            + sdSegs
            + " searched="
            + sdSearched.size()
            + " deleted="
            + sdDeleted.size()
            + " searchedEqualsTwoSegmentSearch="
            + sdSearched.equals(searched)
            + " -> "
            + (sdSame ? "MATCHES search" : "DEVIATES from search"));
    if (sdSegs != 1 || !sdSame || !sdSearched.equals(searched)) {
      System.out.println("  PROBE PROBLEM: the one-segment control does not behave as expected");
      return 3;
    }

    if (segCount != segSizes.length) {
      System.out.println("  PROBE PROBLEM: expected " + segSizes.length + " segments");
      return 3;
    }
    if (searched.size() != expectedHits) {
      System.out.println(
          "  PROBE PROBLEM: expected " + expectedHits + " search hits, got " + searched.size());
      return 3;
    }
    if (isControl && !same) {
      System.out.println("  PROBE PROBLEM: the per-document control deviates");
      return 3;
    }
    return same ? 0 : 1;
  }

  /** Segments [from, to) of the layout, one commit per segment; ids are global. */
  static Directory build(int[] segSizes, int from, int to, DocMaker dm) throws Exception {
    return build(segSizes, from, to, dm, false);
  }

  /** As above; with oneSegment, a single commit at the end, so all documents form one segment. */
  static Directory build(int[] segSizes, int from, int to, DocMaker dm, boolean oneSegment)
      throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, config());
    int id = 0;
    for (int s = 0; s < to; s++) {
      for (int i = 0; i < segSizes[s]; i++, id++) {
        if (s < from) continue;
        Document doc = new Document();
        doc.add(new StoredField("id", Integer.toString(id)));
        dm.addFields(doc, i, id);
        w.addDocument(doc);
      }
      if (s >= from && !oneSegment) w.commit();
    }
    w.commit();
    w.close();
    return dir;
  }

  static Set<String> ids(Directory dir, Query q) throws Exception {
    Set<String> out = new TreeSet<>();
    DirectoryReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    Object stored = null;
    Method docMethod;
    try {
      Method sf = IndexSearcher.class.getMethod("storedFields");
      stored = sf.invoke(searcher);
      docMethod = sf.getReturnType().getMethod("document", int.class);
    } catch (NoSuchMethodException e) {
      stored = searcher;
      docMethod = IndexSearcher.class.getMethod("doc", int.class);
    }
    for (ScoreDoc sd : searcher.search(q, Math.max(1, reader.maxDoc())).scoreDocs) {
      Document d = (Document) docMethod.invoke(stored, sd.doc);
      out.add(d.get("id"));
    }
    reader.close();
    return out;
  }

  static int segmentCount(Directory dir) throws Exception {
    DirectoryReader reader = DirectoryReader.open(dir);
    int n = reader.leaves().size();
    reader.close();
    return n;
  }

  static IndexWriterConfig config() throws Exception {
    Analyzer a = analyzer();
    try {
      return IndexWriterConfig.class.getConstructor(Analyzer.class).newInstance(a);
    } catch (NoSuchMethodException e) {
      Class<?> v = Class.forName("org.apache.lucene.util.Version");
      return IndexWriterConfig.class.getConstructor(v, Analyzer.class).newInstance(version(), a);
    }
  }

  static Object version() throws Exception {
    Class<?> v = Class.forName("org.apache.lucene.util.Version");
    try {
      return v.getField("LATEST").get(null);
    } catch (NoSuchFieldException e) {
      return v.getField("LUCENE_CURRENT").get(null);
    }
  }

  static Analyzer analyzer() throws Exception {
    Class<?> sa = Class.forName("org.apache.lucene.analysis.standard.StandardAnalyzer");
    try {
      return (Analyzer) sa.getConstructor().newInstance();
    } catch (NoSuchMethodException e) {
      Class<?> v = Class.forName("org.apache.lucene.util.Version");
      return (Analyzer) sa.getConstructor(v).newInstance(version());
    }
  }

  static Directory newDirectory() throws Exception {
    Class<?> c = classOrNull("org.apache.lucene.store.ByteBuffersDirectory");
    if (c == null) c = Class.forName("org.apache.lucene.store.RAMDirectory");
    return (Directory) c.getConstructor().newInstance();
  }

  static Class<?> classOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  /** Distinct one-substitution or two-substitution variants of "lucenex" (as in the unit repro). */
  static List<String> variants(int edits, int count) {
    List<String> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String base = "lucenex";
    String letters = "bcdfghjkmnpqrstvwz";
    for (int p = 0; p < base.length() && out.size() < count; p++) {
      for (int l = 0; l < letters.length() && out.size() < count; l++) {
        char[] c = base.toCharArray();
        c[p] = letters.charAt(l);
        if (edits == 2) {
          c[(p + 2) % base.length()] = 'y';
        }
        String s = new String(c);
        if (!s.equals(base) && seen.add(s)) {
          out.add(s);
        }
      }
    }
    if (out.size() != count) throw new IllegalStateException("only " + out.size() + " variants");
    return out;
  }
}
