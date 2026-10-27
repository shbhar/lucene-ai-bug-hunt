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

import java.util.Locale;
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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * BM25NBClassifier should not assign a label that has no training document. A label can be in the
 * class field's term dictionary without any live document that matches the training query: the
 * documented training query can exclude all its documents, or all its documents can be deleted (the
 * term stays until a merge drops it). Such a label currently gets log score 0, which beats the
 * trained labels here because their BM25 scores are below 1.
 */
public class TestBM25NBUntrainedLabel extends LuceneTestCase {

  private static final String TEXT = "text";
  private static final String LABEL = "label";
  private static final String SPLIT = "split";

  private static final String[][] TRAINING = {
    {"apple banana", "fruit"},
    {"apple pear", "fruit"},
    {"car truck", "vehicle"},
    {"car bus", "vehicle"}
  };

  private static void add(IndexWriter w, String text, String label, String split) throws Exception {
    Document doc = new Document();
    doc.add(new TextField(TEXT, text, Field.Store.NO));
    doc.add(new StringField(LABEL, label, Field.Store.NO));
    doc.add(new StringField(SPLIT, split, Field.Store.NO));
    w.addDocument(doc);
  }

  private static void addTraining(IndexWriter w) throws Exception {
    for (String[] t : TRAINING) {
      add(w, t[0], t[1], "train");
    }
  }

  /** Classifies "apple" and fails unless the answer is "fruit"; the message lists every label. */
  private static void assertFruit(IndexReader reader, Analyzer analyzer, Query trainingQuery)
      throws Exception {
    BM25NBClassifier classifier =
        new BM25NBClassifier(reader, analyzer, trainingQuery, LABEL, TEXT);
    String assigned = classifier.assignClass("apple").assignedClass().utf8ToString();
    StringBuilder all = new StringBuilder();
    for (ClassificationResult<BytesRef> c : classifier.getClasses("apple")) {
      all.append(all.length() == 0 ? "" : ", ").append(c.assignedClass().utf8ToString());
      all.append(String.format(Locale.ROOT, " %.3f", c.score()));
    }
    assertTrue(
        "'apple' was assigned to " + assigned + ", expected fruit; getClasses: [" + all + "]",
        assigned.equals("fruit"));
  }

  private static int count(IndexReader reader, String label, Query trainingQuery) throws Exception {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(new TermQuery(new Term(LABEL, label)), BooleanClause.Occur.FILTER);
    if (trainingQuery != null) {
      b.add(trainingQuery, BooleanClause.Occur.FILTER);
    }
    return new IndexSearcher(reader).count(b.build());
  }

  /** Control: the same training documents without the extra label give "fruit". */
  public void testControlWithoutExtraLabel() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        addTraining(w);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query train = new TermQuery(new Term(SPLIT, "train"));
        assertFruit(reader, analyzer, null);
        assertFruit(reader, analyzer, train);
      }
    }
    analyzer.close();
  }

  /** The training query split:train leaves out the only document of label "heldout". */
  public void testLabelExcludedByTrainingQuery() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        addTraining(w);
        add(w, "zzz", "heldout", "test");
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query train = new TermQuery(new Term(SPLIT, "train"));
        assertEquals(1, count(reader, "heldout", null)); // the label is in the index
        assertEquals(0, count(reader, "heldout", train)); // but has no training document
        assertFruit(reader, analyzer, train);
      }
    }
    analyzer.close();
  }

  /** The only document of label "gone" is deleted; no merge has dropped its term yet. */
  public void testLabelWhoseDocumentsAreAllDeleted() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        addTraining(w);
        add(w, "zzz", "gone", "train");
        w.commit();
        w.deleteDocuments(new Term(LABEL, "gone"));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDeletedDocs()); // the delete is applied
        assertTrue(MultiTerms.getTerms(reader, LABEL).iterator().seekExact(new BytesRef("gone")));
        assertEquals(0, count(reader, "gone", null)); // no live document has the label
        assertFruit(reader, analyzer, null);
      }
    }
    analyzer.close();
  }
}
