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

import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.classification.document.KNearestNeighborDocumentClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The k-NN classifiers add the training query ("a Query to eventually filter the docs used for
 * training the classifier") and the class-field restriction as scoring MUST clauses next to the
 * similarity clauses, so their scores are added to every neighbour's similarity. A training query
 * that filters out nothing therefore changes which documents are the nearest neighbours, their
 * weights, the class scores and the assigned class. BooleanPerceptronClassifier builds the same
 * kind of query and trains in its score order, so the same filter reorders its training.
 */
public class TestKnnTrainingFilterIsScored extends LuceneTestCase {

  private static final String TEXT = "text";
  private static final String CLASS = "cls";
  private static final String SPLIT = "split";

  private static Analyzer analyzer() {
    return new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
  }

  private static void add(IndexWriter w, String text, String cls, String split) throws Exception {
    Document d = new Document();
    d.add(new TextField(TEXT, text, Field.Store.YES));
    d.add(new StringField(CLASS, cls, Field.Store.YES));
    d.add(new TextField(SPLIT, split, Field.Store.NO));
    w.addDocument(d);
  }

  private static String assigned(Classifier<BytesRef> c, String text) throws Exception {
    ClassificationResult<BytesRef> r = c.assignClass(text);
    return r == null ? null : r.assignedClass().utf8ToString();
  }

  /**
   * Both training documents contain the term split:train, so TermQuery(split:train) filters out
   * nothing. The nearest neighbour of "aa" is d1 ("aa"); with the filter, the higher BM25 score of
   * split:train on d0 (shorter split field) makes d0 ("aa bb", class Y) the nearest neighbour.
   */
  public void testTrainingQueryThatMatchesEverythingChangesNearestNeighbour() throws Exception {
    Analyzer analyzer = analyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        add(w, "aa bb", "Y", "train");
        add(w, "aa", "X", "train pad");
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query everything = new TermQuery(new Term(SPLIT, "train"));
        assertEquals(2, newSearcher(reader).count(everything));
        KNearestNeighborClassifier noFilter =
            new KNearestNeighborClassifier(reader, null, analyzer, null, 1, 1, 1, CLASS, TEXT);
        KNearestNeighborClassifier withFilter =
            new KNearestNeighborClassifier(reader, null, analyzer, everything, 1, 1, 1, CLASS, TEXT);
        assertEquals("X", assigned(noFilter, "aa"));
        assertEquals(
            "a training query that matches every document changed the assigned class",
            assigned(noFilter, "aa"),
            assigned(withFilter, "aa"));
      }
    }
  }

  /** MatchAllDocsQuery as the training query filters out nothing, yet flips the class. */
  public void testMatchAllTrainingQueryChangesAssignedClass() throws Exception {
    Analyzer analyzer = analyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        add(w, "aa bb", "Y", "train");
        add(w, "aa", "X", "train");
        add(w, "aa", "X", "train");
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        KNearestNeighborClassifier noFilter =
            new KNearestNeighborClassifier(reader, null, analyzer, null, 3, 1, 1, CLASS, TEXT);
        KNearestNeighborClassifier matchAll =
            new KNearestNeighborClassifier(
                reader, null, analyzer, MatchAllDocsQuery.INSTANCE, 3, 1, 1, CLASS, TEXT);
        String text = "aa bb bb";
        assertEquals("Y", assigned(noFilter, text));
        assertEquals(
            "MatchAllDocsQuery as training query changed the assigned class; classes without "
                + noFilter.getClasses(text)
                + " with "
                + matchAll.getClasses(text),
            assigned(noFilter, text),
            assigned(matchAll, text));
      }
    }
  }

  /** The same for the fuzzy and the document variants: the scores change. */
  public void testMatchAllTrainingQueryChangesScoresOfOtherVariants() throws Exception {
    Analyzer analyzer = analyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        add(w, "bb", "X", "train");
        add(w, "aa", "X", "train");
        add(w, "aa aa", "X", "train");
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        KNearestFuzzyClassifier fuzzy =
            new KNearestFuzzyClassifier(reader, null, analyzer, null, 2, CLASS, TEXT);
        KNearestFuzzyClassifier fuzzyAll =
            new KNearestFuzzyClassifier(
                reader, null, analyzer, MatchAllDocsQuery.INSTANCE, 2, CLASS, TEXT);
        List<ClassificationResult<BytesRef>> a = fuzzy.getClasses("aa");
        List<ClassificationResult<BytesRef>> b = fuzzyAll.getClasses("aa");

        KNearestNeighborDocumentClassifier docKnn =
            new KNearestNeighborDocumentClassifier(
                reader, null, null, 2, 1, 1, CLASS, Collections.singletonMap(TEXT, analyzer), TEXT);
        KNearestNeighborDocumentClassifier docKnnAll =
            new KNearestNeighborDocumentClassifier(
                reader,
                null,
                MatchAllDocsQuery.INSTANCE,
                2,
                1,
                1,
                CLASS,
                Collections.singletonMap(TEXT, analyzer),
                TEXT);
        Document input = new Document();
        input.add(new TextField(TEXT, "aa", Field.Store.YES));
        List<ClassificationResult<BytesRef>> c = docKnn.getClasses(input);
        List<ClassificationResult<BytesRef>> d = docKnnAll.getClasses(input);
        assertTrue(
            "KNearestFuzzyClassifier: " + a + " vs " + b + "; KNearestNeighborDocumentClassifier: "
                + c + " vs " + d,
            a.equals(b) && c.equals(d));
      }
    }
  }

  /**
   * BooleanPerceptronClassifier visits its training documents in the score order of the same kind
   * of query, so a training query that matches every document reorders the training and changes
   * the learned model.
   */
  public void testPerceptronTrainingQueryThatMatchesEverythingChangesModel() throws Exception {
    Analyzer analyzer = analyzer();
    org.apache.lucene.document.FieldType tv =
        new org.apache.lucene.document.FieldType(TextField.TYPE_STORED);
    tv.setStoreTermVectors(true);
    tv.freeze();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        String[][] docs = {{"aa", "true", "train pad"}, {"aa", "false", "train"}};
        for (String[] d : docs) {
          Document doc = new Document();
          doc.add(new Field(TEXT, d[0], tv));
          doc.add(new StringField(CLASS, d[1], Field.Store.YES));
          doc.add(new TextField(SPLIT, d[2], Field.Store.NO));
          w.addDocument(doc);
        }
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query everything = new TermQuery(new Term(SPLIT, "train"));
        assertEquals(2, newSearcher(reader).count(everything));
        BooleanPerceptronClassifier noFilter =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, 1.5, CLASS, TEXT);
        BooleanPerceptronClassifier withFilter =
            new BooleanPerceptronClassifier(reader, analyzer, everything, 1, 1.5, CLASS, TEXT);
        assertEquals(
            "a training query that matches every document changed the perceptron's answer",
            noFilter.assignClass("aa").assignedClass(),
            withFilter.assignClass("aa").assignedClass());
      }
    }
  }
}
