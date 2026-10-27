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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.BM25NBClassifier;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/**
 * Prints the numbers behind the brief: for each label, the BM25 top score of the classifier's
 * prior query (label MUST + training query MUST) and of its likelihood query for each input word
 * (label MUST + word SHOULD + training query MUST), their logs and sum, and the classifier's own
 * normalised scores; then an input sweep, the forceMergeDeletes workaround, a training query that
 * matches nothing, and getClasses(text, max). Public API only; no randomness.
 */
public class BM25NBProbe {
  static final String TEXT = "text", LABEL = "label", SPLIT = "split";
  static final String[][] TRAINING = {
    {"apple banana", "fruit"}, {"apple pear", "fruit"}, {"car truck", "vehicle"}, {"car bus", "vehicle"}
  };
  static final String[] INPUTS = {"apple", "car", "apple pear", "car truck bus", "banana"};
  static final Analyzer A = new StandardAnalyzer();

  static void add(IndexWriter w, String text, String label, String split) throws Exception {
    Document d = new Document();
    d.add(new TextField(TEXT, text, Field.Store.NO));
    d.add(new StringField(LABEL, label, Field.Store.NO));
    d.add(new StringField(SPLIT, split, Field.Store.NO));
    w.addDocument(d);
  }

  static Directory build(String extraLabel, String extraSplit, boolean deleteExtra) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(A))) {
      for (String[] t : TRAINING) add(w, t[0], t[1], "train");
      if (extraLabel != null) add(w, "zzz", extraLabel, extraSplit);
      w.commit();
      if (deleteExtra) w.deleteDocuments(new Term(LABEL, extraLabel));
    }
    return dir;
  }

  static double top(IndexSearcher s, Query q) throws Exception {
    TopDocs td = s.search(q, 1);
    return td.totalHits.value() > 0 ? td.scoreDocs[0].score : Double.NaN;
  }

  static String f(double v) {
    return Double.isNaN(v) ? "no hit" : String.format(Locale.ROOT, "%.4f", v);
  }

  static String classes(List<ClassificationResult<BytesRef>> l) {
    if (l == null) return "null";
    StringBuilder sb = new StringBuilder("[");
    for (ClassificationResult<BytesRef> c : l) {
      sb.append(sb.length() == 1 ? "" : ", ").append(c.assignedClass().utf8ToString());
      sb.append(String.format(Locale.ROOT, " %.3f", c.score()));
    }
    return sb.append(']').toString();
  }

  static String assigned(BM25NBClassifier c, String text) throws Exception {
    ClassificationResult<BytesRef> r = c.assignClass(text);
    return r == null ? "null" : r.assignedClass().utf8ToString();
  }

  /** Replays the classifier's two searches for every label and prints the parts of its score. */
  static void explain(String title, IndexReader reader, Query train, String input) throws Exception {
    System.out.println("== " + title + ", training query " + (train == null ? "null" : train) + ", input '" + input + "'");
    IndexSearcher s = new IndexSearcher(reader);
    s.setSimilarity(new BM25Similarity());
    Terms terms = MultiTerms.getTerms(reader, LABEL);
    TermsEnum te = terms.iterator();
    BytesRef next;
    List<String> labels = new ArrayList<>();
    List<Double> totals = new ArrayList<>();
    while ((next = te.next()) != null) {
      Term label = new Term(LABEL, BytesRef.deepCopyOf(next));
      BooleanQuery.Builder prior = new BooleanQuery.Builder();
      prior.add(new TermQuery(label), BooleanClause.Occur.MUST);
      if (train != null) prior.add(train, BooleanClause.Occur.MUST);
      double p = top(s, prior.build());
      double logPrior = Double.isNaN(p) ? 0 : Math.log(p); // the code's fallback: 0
      StringBuilder line = new StringBuilder();
      line.append(String.format(Locale.ROOT, "  %-8s prior: top score %s, log %s", label.text(), f(p), String.format(Locale.ROOT, "%.4f", logPrior)));
      double total = logPrior;
      for (String word : input.split(" ")) {
        BooleanQuery.Builder lik = new BooleanQuery.Builder();
        lik.add(new TermQuery(label), BooleanClause.Occur.MUST);
        lik.add(new TermQuery(new Term(TEXT, word)), BooleanClause.Occur.SHOULD);
        if (train != null) lik.add(train, BooleanClause.Occur.MUST);
        double l = top(s, lik.build());
        double logL = Math.log(Double.isNaN(l) ? 1 : l); // the code's fallback: probability 1
        line.append(String.format(Locale.ROOT, " | '%s': top score %s, log %.4f", word, f(l), logL));
        total += logL;
      }
      line.append(String.format(Locale.ROOT, " | total %.4f", total));
      System.out.println(line);
      labels.add(label.text());
      totals.add(total);
    }
    double sum = 0;
    for (double t : totals) sum += Math.exp(t);
    StringBuilder rec = new StringBuilder("  softmax of the totals:");
    for (int i = 0; i < labels.size(); i++) {
      rec.append(String.format(Locale.ROOT, " %s %.3f", labels.get(i), Math.exp(totals.get(i)) / sum));
    }
    System.out.println(rec);
    BM25NBClassifier c = new BM25NBClassifier(reader, A, train, LABEL, TEXT);
    System.out.println("  classifier: assignClass = " + assigned(c, input) + ", getClasses = " + classes(c.getClasses(input)));
  }

  static void sweep(String title, IndexReader reader, Query train, String extra) throws Exception {
    BM25NBClassifier c = new BM25NBClassifier(reader, A, train, LABEL, TEXT);
    int n = 0;
    StringBuilder sb = new StringBuilder();
    for (String in : INPUTS) {
      String a = assigned(c, in);
      if (a.equals(extra)) n++;
      sb.append(" '").append(in).append("'->").append(a);
    }
    System.out.println("== sweep " + title + ": " + n + " of " + INPUTS.length + " inputs assigned to " + extra + ":" + sb);
  }

  public static void main(String[] args) throws Exception {
    Query train = new TermQuery(new Term(SPLIT, "train"));
    try (Directory d = build(null, null, false); DirectoryReader r = DirectoryReader.open(d)) {
      explain("control (no extra label)", r, null, "apple");
      explain("control (no extra label)", r, train, "apple");
      sweep("control, null query", r, null, "-");
    }
    try (Directory d = build("heldout", "test", false); DirectoryReader r = DirectoryReader.open(d)) {
      explain("label heldout excluded by the training query", r, train, "apple");
      explain("label heldout excluded by the training query", r, train, "apple pear");
      sweep("heldout excluded", r, train, "heldout");
      BM25NBClassifier c = new BM25NBClassifier(r, A, train, LABEL, TEXT);
      System.out.println("== getClasses('apple', 3) = " + classes(c.getClasses("apple", 3)));
      Query none = new TermQuery(new Term(SPLIT, "nothing"));
      BM25NBClassifier empty = new BM25NBClassifier(r, A, none, LABEL, TEXT);
      String res;
      try {
        res = "assignClass = " + assigned(empty, "apple") + ", getClasses = " + classes(empty.getClasses("apple"));
      } catch (RuntimeException e) {
        res = "throws " + e;
      }
      System.out.println("== training query matching no document (split:nothing): " + res);
    }
    try (Directory d = build("gone", "train", true); DirectoryReader r = DirectoryReader.open(d)) {
      System.out.println("== deleted variant: numDocs " + r.numDocs() + ", numDeletedDocs " + r.numDeletedDocs());
      explain("label gone, its only document deleted", r, null, "apple");
      sweep("gone deleted", r, null, "gone");
      try (IndexWriter w = new IndexWriter(d, new IndexWriterConfig(A))) {
        w.forceMergeDeletes();
      }
      try (DirectoryReader r2 = DirectoryReader.open(d)) {
        boolean term = MultiTerms.getTerms(r2, LABEL).iterator().seekExact(new BytesRef("gone"));
        BM25NBClassifier c = new BM25NBClassifier(r2, A, null, LABEL, TEXT);
        System.out.println("== workaround forceMergeDeletes(): numDeletedDocs " + r2.numDeletedDocs() + ", term 'gone' still in dictionary: " + term + ", 'apple' -> " + assigned(c, "apple") + " " + classes(c.getClasses("apple")));
        sweep("gone after forceMergeDeletes", r2, null, "gone");
      }
    }
  }
}
