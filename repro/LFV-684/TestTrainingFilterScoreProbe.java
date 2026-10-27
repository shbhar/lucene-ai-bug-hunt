package org.apache.lucene.classification;

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Prints the numbers behind the doc's lead examples: each neighbour's similarity-only score, the
 * score the classifier actually ranks and weights by (similarity + class-field clause + training
 * query), the resulting vote weights and the classifier's own class scores. Rebuilds knnSearch's
 * query with the public MoreLikeThis API, using the classifier's settings (minDocFreq 1, minTermFreq
 * 1, boost on, BM25), and prints the rebuilt class scores next to getClasses() (they agree on stock
 * main; nothing is asserted).
 */
public class TestTrainingFilterScoreProbe extends LuceneTestCase {
  static final String TEXT = "text", CLASS = "cls", SPLIT = "split";

  static void add(IndexWriter w, String text, String cls, String split) throws Exception {
    Document d = new Document();
    d.add(new TextField(TEXT, text, Field.Store.YES));
    d.add(new StringField(CLASS, cls, Field.Store.YES));
    d.add(new TextField(SPLIT, split, Field.Store.NO));
    w.addDocument(d);
  }

  static Query similarityOnly(DirectoryReader r, Analyzer a, String text) throws Exception {
    MoreLikeThis mlt = new MoreLikeThis(r);
    mlt.setAnalyzer(a);
    mlt.setFieldNames(new String[] {TEXT});
    mlt.setMinDocFreq(1);
    mlt.setMinTermFreq(1);
    mlt.setBoost(true);
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(mlt.like(TEXT, new StringReader(text)), BooleanClause.Occur.SHOULD);
    return b.build();
  }

  /** knnSearch's query as built on main: MLT SHOULD + class:* MUST (+ training query MUST). */
  static Query asClassifier(DirectoryReader r, Analyzer a, String text, Query training)
      throws Exception {
    MoreLikeThis mlt = new MoreLikeThis(r);
    mlt.setAnalyzer(a);
    mlt.setFieldNames(new String[] {TEXT});
    mlt.setMinDocFreq(1);
    mlt.setMinTermFreq(1);
    mlt.setBoost(true);
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(mlt.like(TEXT, new StringReader(text)), BooleanClause.Occur.SHOULD);
    b.add(new WildcardQuery(new Term(CLASS, "*")), BooleanClause.Occur.MUST);
    if (training != null) b.add(training, BooleanClause.Occur.MUST);
    return b.build();
  }

  static void show(
      String label, DirectoryReader r, IndexSearcher s, Analyzer a, String text, Query training, int k)
      throws Exception {
    TopDocs sim = s.search(similarityOnly(r, a, text), 10);
    TopDocs td = s.search(asClassifier(r, a, text, training), k);
    System.out.println("  [" + label + "] top-" + k + " as ranked by the classifier:");
    float max = td.scoreDocs[0].score;
    double x = 0, y = 0;
    for (ScoreDoc sd : td.scoreDocs) {
      String t = s.storedFields().document(sd.doc).get(TEXT);
      String c = s.storedFields().document(sd.doc).get(CLASS);
      float simScore = 0f;
      for (ScoreDoc q : sim.scoreDocs) if (q.doc == sd.doc) simScore = q.score;
      double w = sd.score / max;
      if (c.equals("X")) x += w;
      else y += w;
      System.out.printf(
          java.util.Locale.ROOT,
          "    doc %d '%s' class %s: similarity %.4f, ranked score %.4f, weight %.4f%n",
          sd.doc, t, c, simScore, sd.score, w);
    }
    System.out.printf(java.util.Locale.ROOT, "    rebuilt class scores: X %.4f, Y %.4f (sum of weights / k)%n", x / k, y / k);
  }

  public void testPrintNumbers() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);

    System.out.println("== Example 1: k = 3, docs 'aa bb'/Y, 'aa'/X, 'aa'/X, input 'aa bb bb'");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        add(w, "aa bb", "Y", "train");
        add(w, "aa", "X", "train");
        add(w, "aa", "X", "train");
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query[] qs = {null, MatchAllDocsQuery.INSTANCE, new BoostQuery(MatchAllDocsQuery.INSTANCE, 0f)};
        String[] names = {"no training query", "MatchAllDocsQuery", "BoostQuery(MatchAllDocsQuery, 0f)"};
        for (int i = 0; i < qs.length; i++) {
          show(names[i], r, s, a, "aa bb bb", qs[i], 3);
          KNearestNeighborClassifier c =
              new KNearestNeighborClassifier(r, null, a, qs[i], 3, 1, 1, CLASS, TEXT);
          System.out.println(
              "    classifier: getClasses = "
                  + c.getClasses("aa bb bb")
                  + ", assignClass = "
                  + c.assignClass("aa bb bb").assignedClass().utf8ToString());
        }
      }
    }

    System.out.println(
        "== Example 2: k = 1, docs 'aa bb'/Y (split 'train'), 'aa'/X (split 'train pad'), input 'aa'");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        add(w, "aa bb", "Y", "train");
        add(w, "aa", "X", "train pad");
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query split = new TermQuery(new Term(SPLIT, "train"));
        System.out.println("  split:train matches " + s.count(split) + " of " + r.numDocs() + " docs");
        for (ScoreDoc sd : s.search(split, 10).scoreDocs) {
          System.out.printf(java.util.Locale.ROOT, "    split:train score of doc %d: %.4f%n", sd.doc, sd.score);
        }
        Query[] qs = {null, split};
        String[] names = {"no training query", "TermQuery(split:train)"};
        for (int i = 0; i < qs.length; i++) {
          show(names[i], r, s, a, "aa", qs[i], 1);
          KNearestNeighborClassifier c =
              new KNearestNeighborClassifier(r, null, a, qs[i], 1, 1, 1, CLASS, TEXT);
          System.out.println(
              "    classifier: assignClass = " + c.assignClass("aa").assignedClass().utf8ToString());
        }
      }
    }
  }

  static void add2(IndexWriter w, String text, String cls, String split, int year) throws Exception {
    Document d = new Document();
    d.add(new TextField(TEXT, text, Field.Store.YES));
    d.add(new StringField(CLASS, cls, Field.Store.YES));
    d.add(new TextField(SPLIT, split, Field.Store.NO));
    d.add(new org.apache.lucene.document.IntPoint("year", year));
    w.addDocument(d);
  }

  public void testOtherShapes() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    System.out.println("== Lead data on the other classifiers and with an IntPoint range");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        add2(w, "aa bb", "Y", "train", 2021);
        add2(w, "aa", "X", "train", 2022);
        add2(w, "aa", "X", "train", 2023);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query range = org.apache.lucene.document.IntPoint.newRangeQuery("year", 2020, 2025);
        System.out.println("  year range matches " + s.count(range) + " of " + r.numDocs());
        Query[] qs = {null, MatchAllDocsQuery.INSTANCE, range, new BoostQuery(range, 0f)};
        String[] names = {"none", "MatchAll", "IntPoint range 2020-2025", "BoostQuery(range, 0f)"};
        Document input = new Document();
        input.add(new TextField(TEXT, "aa bb bb", Field.Store.YES));
        for (int i = 0; i < qs.length; i++) {
          KNearestNeighborClassifier c =
              new KNearestNeighborClassifier(r, null, a, qs[i], 3, 1, 1, CLASS, TEXT);
          org.apache.lucene.classification.document.KNearestNeighborDocumentClassifier dc =
              new org.apache.lucene.classification.document.KNearestNeighborDocumentClassifier(
                  r, null, qs[i], 3, 1, 1, CLASS, java.util.Collections.singletonMap(TEXT, a), TEXT);
          KNearestFuzzyClassifier fc = new KNearestFuzzyClassifier(r, null, a, qs[i], 3, CLASS, TEXT);
          System.out.println("  [" + names[i] + "] knn " + c.getClasses("aa bb bb")
              + " | doc " + dc.getClasses(input) + " | fuzzy " + fc.getClasses("aa bb bb"));
        }
      }
    }
    System.out.println("== k = 1 candidates (text-field filter matched by every doc)");
    String[][][] designs = {
      {{"aa bb", "Y", "train"}, {"aa", "X", "train pad"}},
      {{"aa bb", "Y", "train"}, {"aa", "X", "train pad pad"}},
      {{"aa bb", "Y", "train"}, {"aa", "X", "train pad pad pad"}},
      {{"aa bb", "Y", "train"}, {"aa", "X", "train 2024 news sports"}},
      {{"aa", "X", "train 2024 news sports"}, {"aa bb", "Y", "train"}},
    };
    for (String[][] dsg : designs) {
      try (Directory dir = new ByteBuffersDirectory()) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
          for (String[] d : dsg) add(w, d[0], d[1], d[2]);
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          IndexSearcher s = new IndexSearcher(r);
          Query split = new TermQuery(new Term(SPLIT, "train"));
          StringBuilder sb = new StringBuilder("  design " + java.util.Arrays.deepToString(dsg) + "\n");
          sb.append("    similarity only:");
          for (ScoreDoc sd : s.search(similarityOnly(r, a, "aa"), 2).scoreDocs) sb.append(String.format(java.util.Locale.ROOT, " doc%d=%.4f", sd.doc, sd.score));
          sb.append("\n    filter only (split:train):");
          for (ScoreDoc sd : s.search(split, 2).scoreDocs) sb.append(String.format(java.util.Locale.ROOT, " doc%d=%.4f", sd.doc, sd.score));
          sb.append("\n");
          for (Query q : new Query[] {null, split}) {
            TopDocs td = s.search(asClassifier(r, a, "aa", q), 2);
            sb.append("    " + (q == null ? "none " : "split") + ":");
            for (ScoreDoc sd : td.scoreDocs) sb.append(String.format(java.util.Locale.ROOT, " doc%d=%.4f", sd.doc, sd.score));
            KNearestNeighborClassifier c = new KNearestNeighborClassifier(r, null, a, q, 1, 1, 1, CLASS, TEXT);
            sb.append(" -> " + c.assignClass("aa").assignedClass().utf8ToString() + "\n");
          }
          System.out.print(sb);
        }
      }
    }
  }
}
