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
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BooleanPerceptronClassifier with batchSize > 1 discards the weight updates of misclassified
 * training documents: an update is written to the pending weight map relative to the weights of
 * the last FST build, the FST is rebuilt only when the misclassified document sits at a position
 * that is a multiple of batchSize, and it is not rebuilt at the end of training.
 */
public class TestBooleanPerceptronBatchUpdates extends LuceneTestCase {

  private static final String TEXT = "text";
  private static final String CLASS = "cls";

  private static void addDoc(IndexWriter w, FieldType ft, String text, boolean cls)
      throws Exception {
    Document doc = new Document();
    doc.add(new Field(TEXT, text, ft));
    doc.add(new StringField(CLASS, Boolean.toString(cls), Field.Store.YES));
    w.addDocument(doc);
  }

  private static FieldType textType() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(true);
    ft.freeze();
    return ft;
  }

  /**
   * Two training documents, both labelled false: "bb" (classified correctly from the start) and
   * "aa aa" (misclassified, since the initial weight of aa is its total term frequency 2, so the
   * output 4 is above the bias 1.5). The only misclassified document is the second one, at batch
   * position 1, so with batchSize 2 its update never reaches the model.
   */
  public void testUpdateOfSecondDocumentInBatchIsLost() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    FieldType ft = textType();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        addDoc(w, ft, "bb", false);
        addDoc(w, ft, "aa aa", false);
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BooleanPerceptronClassifier online =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, 1.5, CLASS, TEXT);
        // control: online training learns from the misclassified document
        assertEquals(Boolean.FALSE, online.assignClass("aa aa").assignedClass());

        BooleanPerceptronClassifier batched =
            new BooleanPerceptronClassifier(reader, analyzer, null, 2, 1.5, CLASS, TEXT);
        // one batch of two documents: its update must be applied as well
        assertEquals(
            "batchSize=2 dropped the update of the misclassified training document 'aa aa'",
            Boolean.FALSE,
            batched.assignClass("aa aa").assignedClass());
      }
    }
  }

  /**
   * Three identical training documents "aa" labelled false, batchSize larger than the training
   * set: only the first document's update is ever applied, so the model still answers true.
   */
  public void testBatchLargerThanTrainingSetLearnsOnlyFirstDocument() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    FieldType ft = textType();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        addDoc(w, ft, "aa", false);
        addDoc(w, ft, "aa", false);
        addDoc(w, ft, "aa", false);
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BooleanPerceptronClassifier online =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, 1.5, CLASS, TEXT);
        assertEquals(Boolean.FALSE, online.assignClass("aa").assignedClass());

        BooleanPerceptronClassifier batched =
            new BooleanPerceptronClassifier(reader, analyzer, null, 10, 1.5, CLASS, TEXT);
        assertEquals(
            "batchSize=10 applied only the first of three misclassified updates",
            Boolean.FALSE,
            batched.assignClass("aa").assignedClass());
      }
    }
  }
}
