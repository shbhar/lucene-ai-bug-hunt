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
package org.apache.lucene.classification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * BM25NBClassifier ("a classifier approximating naive bayes") must not assign a class that has no
 * training document. Two legal ways to get such a class into the class field's term dictionary:
 *
 * <ol>
 *   <li>all documents of the class were deleted (the term stays until the segment is merged);
 *   <li>the documented training-filter {@code query} excludes every document of the class.
 * </ol>
 *
 * Oracle 1: the assigned class has at least one live document that matches the training query.
 * Oracle 2 (metamorphic): the result equals the result on an index that never contained the extra
 * class.
 */
public class TestBM25NBClassWithoutTrainingDocs extends LuceneTestCase {

  private static final String BODY = "body";
  private static final String LABEL = "label";
  private static final String SPLIT = "split";

  private static final String[][] TRAINING = {
    {"goal team match", "sports"},
    {"team referee goal", "sports"},
    {"match stadium team", "sports"},
    {"oven bread bake", "cooking"},
    {"flour bread oven", "cooking"},
    {"bake cake flour", "cooking"},
  };

  private static final String EXTRA_CLASS = "archived";
  private static final String EXTRA_TEXT = "old story notes";

  private static final String[] INPUTS = {
    "goal", "team match", "bread oven", "bake", "goal team match stadium referee", "cake flour bake"
  };

  private static void add(IndexWriter w, String body, String label, String split)
      throws IOException {
    Document d = new Document();
    d.add(new TextField(BODY, body, Field.Store.NO));
    d.add(new StringField(LABEL, label, Field.Store.NO));
    d.add(new StringField(SPLIT, split, Field.Store.NO));
    w.addDocument(d);
  }

  private static void addTraining(IndexWriter w) throws IOException {
    for (String[] t : TRAINING) {
      add(w, t[0], t[1], "train");
    }
  }

  private static boolean classTermExists(IndexReader r, String label) throws IOException {
    Terms terms = MultiTerms.getTerms(r, LABEL);
    TermsEnum te = terms.iterator();
    return te.seekExact(new BytesRef(label));
  }

  private static int trainingDocsOfClass(IndexReader r, String label, Query trainingQuery)
      throws IOException {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(new TermQuery(new Term(LABEL, label)), BooleanClause.Occur.FILTER);
    if (trainingQuery != null) {
      b.add(trainingQuery, BooleanClause.Occur.FILTER);
    }
    return new IndexSearcher(r).count(b.build());
  }

  private static String describe(List<ClassificationResult<BytesRef>> classes) {
    StringBuilder sb = new StringBuilder();
    for (ClassificationResult<BytesRef> cr : classes) {
      sb.append(cr.assignedClass().utf8ToString()).append('=').append(cr.score()).append(' ');
    }
    return sb.toString().trim();
  }

  /**
   * Classifies every input on {@code r} and on {@code baseline}; returns one message per input
   * whose assigned class has no training document, or differs from the baseline.
   */
  private static List<String> check(
      IndexReader r, IndexReader baseline, Analyzer a, Query trainingQuery) throws IOException {
    BM25NBClassifier c = new BM25NBClassifier(r, a, trainingQuery, LABEL, BODY);
    BM25NBClassifier cBase = new BM25NBClassifier(baseline, a, trainingQuery, LABEL, BODY);
    List<String> problems = new ArrayList<>();
    for (String input : INPUTS) {
      String assigned = c.assignClass(input).assignedClass().utf8ToString();
      String expected = cBase.assignClass(input).assignedClass().utf8ToString();
      int docs = trainingDocsOfClass(r, assigned, trainingQuery);
      if (docs == 0 || !assigned.equals(expected)) {
        problems.add(
            "input='"
                + input
                + "' assigned="
                + assigned
                + " (training docs of that class="
                + docs
                + ") baseline="
                + expected
                + " getClasses=["
                + describe(c.getClasses(input))
                + "]");
      }
    }
    return problems;
  }

  private Directory baselineIndex(Analyzer a) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      addTraining(w);
    }
    return dir;
  }

  /** Sanity control: without the extra class, every input is classified as expected. */
  public void testBaselineControl() throws Exception {
    Analyzer a = new StandardAnalyzer();
    try (Directory base = baselineIndex(a);
        DirectoryReader rb = DirectoryReader.open(base)) {
      BM25NBClassifier c = new BM25NBClassifier(rb, a, null, LABEL, BODY);
      String[] expected = {"sports", "sports", "cooking", "cooking", "sports", "cooking"};
      for (int i = 0; i < INPUTS.length; i++) {
        assertEquals(
            INPUTS[i], expected[i], c.assignClass(INPUTS[i]).assignedClass().utf8ToString());
      }
    }
    a.close();
  }

  /** Variant 1: the extra class's only document is deleted (not yet merged away). */
  public void testClassWithOnlyDeletedDocs() throws Exception {
    Analyzer a = new StandardAnalyzer();
    try (Directory base = baselineIndex(a);
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        addTraining(w);
        add(w, EXTRA_TEXT, EXTRA_CLASS, "train");
        w.commit();
        w.deleteDocuments(new Term(LABEL, EXTRA_CLASS));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          DirectoryReader rb = DirectoryReader.open(base)) {
        // preconditions: the deletion is applied, the class term is still in the dictionary
        assertEquals(1, r.numDeletedDocs());
        assertEquals(TRAINING.length, r.numDocs());
        assertTrue(classTermExists(r, EXTRA_CLASS));
        assertEquals(0, trainingDocsOfClass(r, EXTRA_CLASS, null));

        List<String> problems = check(r, rb, a, null);
        assertTrue(
            problems.size()
                + "/"
                + INPUTS.length
                + " inputs assigned to a class with no live document:\n"
                + String.join("\n", problems),
            problems.isEmpty());
      }
    }
    a.close();
  }

  /** Variant 2: the extra class's only document is excluded by the training query. */
  public void testClassExcludedByTrainingQuery() throws Exception {
    Analyzer a = new StandardAnalyzer();
    Query train = new TermQuery(new Term(SPLIT, "train"));
    try (Directory base = baselineIndex(a);
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        addTraining(w);
        add(w, EXTRA_TEXT, EXTRA_CLASS, "holdout");
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          DirectoryReader rb = DirectoryReader.open(base)) {
        assertEquals(0, r.numDeletedDocs());
        assertTrue(classTermExists(r, EXTRA_CLASS));
        assertEquals(1, trainingDocsOfClass(r, EXTRA_CLASS, null));
        assertEquals(0, trainingDocsOfClass(r, EXTRA_CLASS, train));

        List<String> problems = check(r, rb, a, train);
        assertTrue(
            problems.size()
                + "/"
                + INPUTS.length
                + " inputs assigned to a class with no training document:\n"
                + String.join("\n", problems),
            problems.isEmpty());
      }
    }
    a.close();
  }
}
