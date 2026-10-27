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

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The SimpleNaiveBayesClassifier constructor takes "a Query to eventually filter the docs used for training the
 * classifier". The filter is applied only to the word counts (getWordFreqForClass). The class prior
 * (calculateLogPrior: docFreq(class) / getDocCount(classField)) and the likelihood denominator
 * (getTextTermFreqForClass: docFreq(class), and docsWithClassSize) are computed over the whole index
 * (SimpleNaiveBayesClassifier.java:166-184, 240-251, 278-284). Documents outside the training set therefore
 * decide the class.
 *
 * <p>Oracle: training with the filter must give the same answer as training on an index that holds only the
 * filtered documents. Training set: three "fruit" docs and one "vehicle" doc; outside it, ten "vehicle" docs.
 */
public class TestSimpleNaiveBayesTrainingQueryPrior extends LuceneTestCase {

  private static void add(IndexWriter w, String text, String cls, String split) throws Exception {
    Document d = new Document();
    d.add(new TextField("text", text, Field.Store.YES));
    d.add(new StringField("cls", cls, Field.Store.YES));
    d.add(new StringField("split", split, Field.Store.YES));
    w.addDocument(d);
  }

  private static void addTrainingDocs(IndexWriter w) throws Exception {
    add(w, "apple", "fruit", "train");
    add(w, "pear", "fruit", "train");
    add(w, "plum", "fruit", "train");
    add(w, "car", "vehicle", "train");
  }

  private static double prob(List<ClassificationResult<BytesRef>> classes, String cls) {
    for (ClassificationResult<BytesRef> c : classes) {
      if (c.assignedClass().utf8ToString().equals(cls)) {
        return c.score();
      }
    }
    return Double.NaN;
  }

  public void testTrainingQueryAppliesToPrior() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory full = newDirectory();
        Directory trainingOnly = newDirectory()) {
      try (IndexWriter w = new IndexWriter(full, new IndexWriterConfig(analyzer))) {
        addTrainingDocs(w);
        for (int i = 0; i < 10; i++) {
          add(w, "truck" + i, "vehicle", "other"); // not training documents
        }
      }
      try (IndexWriter w = new IndexWriter(trainingOnly, new IndexWriterConfig(analyzer))) {
        addTrainingDocs(w);
      }
      try (DirectoryReader fullReader = DirectoryReader.open(full);
          DirectoryReader trainingReader = DirectoryReader.open(trainingOnly)) {
        Query trainingDocs = new TermQuery(new Term("split", "train"));
        SimpleNaiveBayesClassifier filtered =
            new SimpleNaiveBayesClassifier(fullReader, analyzer, trainingDocs, "cls", "text");
        SimpleNaiveBayesClassifier reference =
            new SimpleNaiveBayesClassifier(trainingReader, analyzer, null, "cls", "text");

        // "apple" occurs only in a fruit training document
        assertEquals("fruit", reference.assignClass("apple").assignedClass().utf8ToString());
        assertEquals(
            "training query ignored: P(fruit|apple)="
                + prob(filtered.getClasses("apple"), "fruit")
                + ", reference="
                + prob(reference.getClasses("apple"), "fruit"),
            "fruit",
            filtered.assignClass("apple").assignedClass().utf8ToString());

        // an input without tokens is classified by the prior alone: P(fruit) = 3/4 in the training set
        assertEquals(0.75, prob(reference.getClasses(""), "fruit"), 1e-9);
        assertEquals("prior P(fruit)", 0.75, prob(filtered.getClasses(""), "fruit"), 1e-9);
      }
    }
  }
}
