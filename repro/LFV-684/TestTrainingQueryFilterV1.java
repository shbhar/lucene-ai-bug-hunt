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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.document.KNearestNeighborDocumentClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: the classifiers document their {@code query} argument as "a Query to
 * eventually filter the docs used for training the classifier, or null if all the indexed docs
 * should be used". A training query that matches every indexed document therefore must give the
 * same result as {@code null}.
 */
public class TestTrainingQueryFilterV1 extends LuceneTestCase {

  private static final String TEXT = "text";
  private static final String CLS = "cls";
  private static final String SPLIT = "split";
  private static final String TAGS = "tags";

  private final Analyzer analyzer = new StandardAnalyzer();

  @Override
  public void tearDown() throws Exception {
    analyzer.close();
    super.tearDown();
  }

  private static Document doc(String text, String cls, String tags) {
    Document d = new Document();
    d.add(new TextField(TEXT, text, Field.Store.YES));
    d.add(new StringField(CLS, cls, Field.Store.YES));
    d.add(new StringField(SPLIT, "train", Field.Store.NO));
    d.add(new TextField(TAGS, tags, Field.Store.NO));
    return d;
  }

  /** class Y: one document sharing both input terms; class X: two longer documents sharing one. */
  private Directory threeNeighbourIndex() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      w.addDocument(doc("aa bb", "Y", "train"));
      w.addDocument(doc("aa f0 f1 f2 f3", "X", "train"));
      w.addDocument(doc("aa g0 g1 g2 g3", "X", "train"));
      for (int i = 0; i < 10; i++) {
        w.addDocument(doc("zz" + i, "Z", "train"));
      }
    }
    return dir;
  }

  private static String str(List<ClassificationResult<BytesRef>> l) {
    List<String> out = new ArrayList<>();
    for (ClassificationResult<BytesRef> c : l) {
      out.add(c.assignedClass().utf8ToString() + "=" + c.score());
    }
    return out.toString();
  }

  private static void assertAllDocsMatch(IndexReader r, Query q) throws Exception {
    assertEquals("precondition: the training query matches every document",
        r.numDocs(), new IndexSearcher(r).count(q));
  }

  private KNearestNeighborClassifier knn(IndexReader r, Query q, int k) throws Exception {
    return new KNearestNeighborClassifier(r, null, analyzer, q, k, 1, 1, CLS, TEXT);
  }

  /** k = 3: MatchAllDocsQuery (filters nothing) flips the assigned class compared to null. */
  public void testKnnMatchAllFlipsAssignedClass() throws Exception {
    try (Directory dir = threeNeighbourIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      Query all = new MatchAllDocsQuery();
      assertAllDocsMatch(r, all);
      String input = "aa bb";
      List<ClassificationResult<BytesRef>> none = knn(r, null, 3).getClasses(input);
      List<ClassificationResult<BytesRef>> withAll = knn(r, all, 3).getClasses(input);
      String msg = "null=" + str(none) + " MatchAllDocsQuery=" + str(withAll);
      assertEquals(msg, "Y", knn(r, null, 3).assignClass(input).assignedClass().utf8ToString());
      assertEquals(
          msg,
          knn(r, null, 3).assignClass(input).assignedClass().utf8ToString(),
          knn(r, all, 3).assignClass(input).assignedClass().utf8ToString());
    }
  }

  /**
   * Control: the same match-everything training query made non-scoring (boost 0) gives exactly the
   * null result, so the difference above comes only from the training query's score.
   */
  public void testKnnZeroBoostMatchAllEqualsNullControl() throws Exception {
    try (Directory dir = threeNeighbourIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      Query all0 = new BoostQuery(new ConstantScoreQuery(new MatchAllDocsQuery()), 0f);
      assertAllDocsMatch(r, all0);
      String input = "aa bb";
      assertEquals(str(knn(r, null, 3).getClasses(input)), str(knn(r, all0, 3).getClasses(input)));
    }
  }

  /** A TermQuery on a StringField that every document has: the class scores change. */
  public void testKnnStringFieldFilterChangesClassScores() throws Exception {
    try (Directory dir = threeNeighbourIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      Query split = new TermQuery(new Term(SPLIT, "train"));
      assertAllDocsMatch(r, split);
      String input = "aa bb";
      assertEquals(
          "class scores with a filter that removes nothing",
          str(knn(r, null, 3).getClasses(input)),
          str(knn(r, split, 3).getClasses(input)));
    }
  }

  /**
   * k = 1: a TermQuery on a multi-valued tags field that every document has ("train") makes the
   * less similar document the nearest neighbour, because its shorter tags field scores higher.
   */
  public void testKnnTagFilterChangesNearestNeighbour() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc("aa", "X", "train news sports"));
        w.addDocument(doc("aa cc", "Y", "train"));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query tag = new TermQuery(new Term(TAGS, "train"));
        assertAllDocsMatch(r, tag);
        String noneCls = knn(r, null, 1).assignClass("aa").assignedClass().utf8ToString();
        String tagCls = knn(r, tag, 1).assignClass("aa").assignedClass().utf8ToString();
        assertEquals("nearest neighbour of 'aa' without a training query", "X", noneCls);
        assertEquals("nearest neighbour of 'aa' with a filter that removes nothing", noneCls, tagCls);
      }
    }
  }

  /** KNearestFuzzyClassifier: MatchAllDocsQuery changes the class scores. */
  public void testFuzzyMatchAllChangesClassScores() throws Exception {
    try (Directory dir = threeNeighbourIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      Query all = new MatchAllDocsQuery();
      String input = "aa bb";
      List<ClassificationResult<BytesRef>> none =
          new KNearestFuzzyClassifier(r, null, analyzer, null, 3, CLS, TEXT).getClasses(input);
      List<ClassificationResult<BytesRef>> withAll =
          new KNearestFuzzyClassifier(r, null, analyzer, all, 3, CLS, TEXT).getClasses(input);
      assertEquals(str(none), str(withAll));
    }
  }

  /** KNearestNeighborDocumentClassifier: MatchAllDocsQuery flips the assigned class. */
  public void testDocumentKnnMatchAllFlipsAssignedClass() throws Exception {
    try (Directory dir = threeNeighbourIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      Document input = new Document();
      input.add(new TextField(TEXT, "aa bb", Field.Store.YES));
      KNearestNeighborDocumentClassifier none =
          new KNearestNeighborDocumentClassifier(
              r, null, null, 3, 1, 1, CLS, Collections.singletonMap(TEXT, analyzer), TEXT);
      KNearestNeighborDocumentClassifier withAll =
          new KNearestNeighborDocumentClassifier(
              r,
              null,
              new MatchAllDocsQuery(),
              3,
              1,
              1,
              CLS,
              Collections.singletonMap(TEXT, analyzer),
              TEXT);
      String msg = "null=" + str(none.getClasses(input)) + " MatchAll=" + str(withAll.getClasses(input));
      assertEquals(
          msg,
          none.assignClass(input).assignedClass().utf8ToString(),
          withAll.assignClass(input).assignedClass().utf8ToString());
    }
  }

  /**
   * BooleanPerceptronClassifier: TermQuery(tags:train) matches every document but scores the doc
   * with the longer tags field lower, so the training visits documents in a different order.
   */
  public void testPerceptronTagFilterChangesModel() throws Exception {
    FieldType tv = new FieldType(TextField.TYPE_STORED);
    tv.setStoreTermVectors(true);
    tv.freeze();
    String[][] docs = {
      {"aa", "true", "train"},
      {"aa", "false", "train"},
      {"aa dd aa", "false", "train t0"},
      {"dd", "true", "train"}
    };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String[] d : docs) {
          Document doc = new Document();
          doc.add(new Field(TEXT, d[0], tv));
          doc.add(new StringField(CLS, d[1], Field.Store.YES));
          doc.add(new TextField(TAGS, d[2], Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query tag = new TermQuery(new Term(TAGS, "train"));
        assertAllDocsMatch(r, tag);
        BooleanPerceptronClassifier none =
            new BooleanPerceptronClassifier(r, analyzer, null, 1, null, CLS, TEXT);
        BooleanPerceptronClassifier withTag =
            new BooleanPerceptronClassifier(r, analyzer, tag, 1, null, CLS, TEXT);
        assertEquals(
            "perceptron answer for 'dd': null=" + none.assignClass("dd") + " tag=" + withTag.assignClass("dd"),
            none.assignClass("dd").assignedClass(),
            withTag.assignClass("dd").assignedClass());
      }
    }
  }

  /**
   * No training query at all: only one document shares terms with the input (class X); the two
   * other neighbours (class Y) share nothing with it but still get weight 1/(1+s) from the
   * constant-score class-field clause and outvote X. Informational: the expected value (X) assumes
   * neighbours are weighted by their similarity alone.
   */
  public void testUnrelatedNeighboursOutvoteOnlySimilarDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc("cc", "Y", "train"));
        w.addDocument(doc("dd", "Y", "train"));
        w.addDocument(doc("aa bb", "X", "train"));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        List<ClassificationResult<BytesRef>> none = knn(r, null, 3).getClasses("aa bb");
        assertEquals(
            "classes " + str(none), "X",
            knn(r, null, 3).assignClass("aa bb").assignedClass().utf8ToString());
      }
    }
  }
}
