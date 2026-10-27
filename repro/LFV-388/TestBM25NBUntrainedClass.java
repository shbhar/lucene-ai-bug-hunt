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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * BM25NBClassifier enumerates every term of the class field, but scores a class whose term has no matching
 * training document with the fallbacks log prior = 0 (BM25NBClassifier.java:230) and term probability = 1, i.e.
 * log-likelihood 0 (:219). Those are the values of a class with probability 1, so a class with no training
 * documents outranks every trained class and is assigned for every input. It happens when the documented
 * training filter ({@code query}) excludes all documents of a class, and when all documents of a class are deleted
 * but their class term is still in the index (not merged away yet).
 */
public class TestBM25NBUntrainedClass extends LuceneTestCase {

  private static void add(IndexWriter w, String text, String cls, String split) throws Exception {
    Document d = new Document();
    d.add(new TextField("text", text, Field.Store.YES));
    d.add(new StringField("cls", cls, Field.Store.YES));
    d.add(new StringField("split", split, Field.Store.YES));
    w.addDocument(d);
  }

  private static String describe(java.util.List<ClassificationResult<BytesRef>> classes) {
    StringBuilder sb = new StringBuilder();
    for (ClassificationResult<BytesRef> c : classes) {
      sb.append(c.assignedClass().utf8ToString()).append('=').append(c.score()).append(' ');
    }
    return sb.toString().trim();
  }

  private static void addTrainingDocs(IndexWriter w) throws Exception {
    add(w, "apple banana", "fruit", "train");
    add(w, "apple pear", "fruit", "train");
    add(w, "car truck", "vehicle", "train");
    add(w, "car bus", "vehicle", "train");
  }

  /** The training filter excludes the only document of class "heldout". */
  public void testClassExcludedByTrainingQuery() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        addTrainingDocs(w);
        add(w, "zzz", "heldout", "test");
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query trainingDocs = new TermQuery(new Term("split", "train"));
        BM25NBClassifier classifier = new BM25NBClassifier(reader, analyzer, trainingDocs, "cls", "text");
        ClassificationResult<BytesRef> result = classifier.assignClass("apple");
        assertEquals(
            "a class without training documents must not be assigned; classes: "
                + describe(classifier.getClasses("apple")),
            "fruit",
            result.assignedClass().utf8ToString());
      }
    }
  }

  /** All documents of class "gone" are deleted; the class term is still enumerated (no merge yet). */
  public void testClassWhoseDocumentsAreDeleted() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        addTrainingDocs(w);
        add(w, "zzz", "gone", "train");
        w.commit();
        w.deleteDocuments(new Term("cls", "gone"));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(4, reader.numDocs());
        BM25NBClassifier classifier = new BM25NBClassifier(reader, analyzer, null, "cls", "text");
        ClassificationResult<BytesRef> result = classifier.assignClass("apple");
        assertEquals(
            "a class whose documents are all deleted must not be assigned; classes: "
                + describe(classifier.getClasses("apple")),
            "fruit",
            result.assignedClass().utf8ToString());
      }
    }
  }
}
