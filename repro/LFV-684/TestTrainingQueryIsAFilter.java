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
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.classification.document.KNearestNeighborDocumentClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The classifiers document their {@code query} argument as "a Query to eventually filter the docs
 * used for training the classifier, or null if all the indexed docs should be used". A training
 * query that keeps every document must therefore give the same result as {@code null}.
 */
public class TestTrainingQueryIsAFilter extends LuceneTestCase {

  private static final String TEXT = "text", CLASS = "cls", TAGS = "tags", YEAR = "year";

  private final Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);

  /** Each doc is {text, class, tags, year}; the text field stores term vectors (perceptron). */
  private Directory index(String[]... docs) throws Exception {
    FieldType textType = new FieldType(TextField.TYPE_STORED);
    textType.setStoreTermVectors(true);
    textType.freeze();
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new Field(TEXT, d[0], textType));
        doc.add(new StringField(CLASS, d[1], Field.Store.YES));
        doc.add(new TextField(TAGS, d[2], Field.Store.NO));
        doc.add(new IntPoint(YEAR, Integer.parseInt(d[3])));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private static String render(List<ClassificationResult<BytesRef>> results) {
    List<String> out = new ArrayList<>();
    for (ClassificationResult<BytesRef> r : results) {
      out.add(String.format(Locale.ROOT, "%s %.4f", r.assignedClass().utf8ToString(), r.score()));
    }
    return out.toString();
  }

  private void assertKeepsEveryDoc(IndexReader reader, Query q) throws Exception {
    assertEquals(reader.numDocs(), newSearcher(reader).count(q));
  }

  private String knn(IndexReader reader, Query q, int k, String text) throws Exception {
    return render(
        new KNearestNeighborClassifier(reader, null, analyzer, q, k, 1, 1, CLASS, TEXT)
            .getClasses(text));
  }

  private static final String[][] THREE_DOCS = {
    {"aa bb", "Y", "train", "2021"}, {"aa", "X", "train", "2022"}, {"aa", "X", "train", "2023"}
  };

  /** k = 3: MatchAllDocsQuery or a year range over every doc flips the class from Y to X. */
  public void testFilterThatKeepsEveryDocChangesKnnClass() throws Exception {
    try (Directory dir = index(THREE_DOCS);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      Query years = IntPoint.newRangeQuery(YEAR, 2020, 2025);
      assertKeepsEveryDoc(reader, years);
      String none = knn(reader, null, 3, "aa bb bb");
      assertEquals(
          "MatchAllDocsQuery: " + none + "; year range: " + none,
          "MatchAllDocsQuery: "
              + knn(reader, MatchAllDocsQuery.INSTANCE, 3, "aa bb bb")
              + "; year range: "
              + knn(reader, years, 3, "aa bb bb"));
    }
  }

  /** The same data and filter, document and fuzzy variants. */
  public void testFilterThatKeepsEveryDocChangesOtherKnnVariants() throws Exception {
    try (Directory dir = index(THREE_DOCS);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      Document input = new Document();
      input.add(new TextField(TEXT, "aa bb bb", Field.Store.NO));
      String[] results = new String[2];
      int i = 0;
      for (Query q : new Query[] {null, MatchAllDocsQuery.INSTANCE}) {
        results[i++] =
            "document: "
                + render(
                    new KNearestNeighborDocumentClassifier(
                            reader,
                            null,
                            q,
                            3,
                            1,
                            1,
                            CLASS,
                            Collections.singletonMap(TEXT, analyzer),
                            TEXT)
                        .getClasses(input))
                + "; fuzzy: "
                + render(
                    new KNearestFuzzyClassifier(reader, null, analyzer, q, 3, CLASS, TEXT)
                        .getClasses("aa bb bb"));
      }
      assertEquals(results[0], results[1]);
    }
  }

  /** k = 1: a term on a text field that every doc has changes the nearest neighbour. */
  public void testTextTermThatKeepsEveryDocChangesNearestNeighbour() throws Exception {
    try (Directory dir =
            index(
                new String[] {"aa", "X", "train news sports 2024", "2024"},
                new String[] {"aa bb", "Y", "train", "2024"});
        DirectoryReader reader = DirectoryReader.open(dir)) {
      Query tag = new TermQuery(new Term(TAGS, "train"));
      assertKeepsEveryDoc(reader, tag);
      assertEquals(knn(reader, null, 1, "aa"), knn(reader, tag, 1, "aa"));
    }
  }

  /** The perceptron trains in the score order of the same kind of query. */
  public void testTextTermThatKeepsEveryDocChangesPerceptronModel() throws Exception {
    try (Directory dir =
            index(
                new String[] {"aa", "true", "train pad", "2024"},
                new String[] {"aa", "false", "train", "2024"});
        DirectoryReader reader = DirectoryReader.open(dir)) {
      Query tag = new TermQuery(new Term(TAGS, "train"));
      assertKeepsEveryDoc(reader, tag);
      boolean none =
          new BooleanPerceptronClassifier(reader, analyzer, null, 1, 1.5, CLASS, TEXT)
              .assignClass("aa")
              .assignedClass();
      boolean filtered =
          new BooleanPerceptronClassifier(reader, analyzer, tag, 1, 1.5, CLASS, TEXT)
              .assignClass("aa")
              .assignedClass();
      assertEquals(none, filtered);
    }
  }

  /** Control: the same filter with a zero score gives the no-filter answer (passes today). */
  public void testZeroScoreFilterGivesTheSameClass() throws Exception {
    try (Directory dir = index(THREE_DOCS);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      Query years = new BoostQuery(IntPoint.newRangeQuery(YEAR, 2020, 2025), 0f);
      assertKeepsEveryDoc(reader, years);
      assertEquals(knn(reader, null, 3, "aa bb bb"), knn(reader, years, 3, "aa bb bb"));
    }
  }
}
