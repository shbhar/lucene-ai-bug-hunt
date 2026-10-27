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
 * Training with batchSize &gt; 1 must apply the weight updates of misclassified training documents,
 * as batchSize 1 does. Training documents are labelled false unless a test says otherwise, the bias
 * is 1.5, and a term's initial weight is its total term frequency in the index. Training visits
 * documents in index order (one segment).
 */
public class TestBooleanPerceptronBatchSize extends LuceneTestCase {

  private static final String TEXT = "text";
  private static final String CLASS = "cls";
  private static final double BIAS = 1.5;

  /** Indexes texts[i] with label labels[i], trains with batchSize, classifies query. */
  private static ClassificationResult<Boolean> train(
      int batchSize, String[] texts, boolean[] labels, String query) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(true);
    ft.freeze();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (int i = 0; i < texts.length; i++) {
          Document doc = new Document();
          doc.add(new Field(TEXT, texts[i], ft));
          doc.add(new StringField(CLASS, Boolean.toString(labels[i]), Field.Store.YES));
          w.addDocument(doc);
        }
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals("one segment, so training order is index order", 1, reader.leaves().size());
        return new BooleanPerceptronClassifier(reader, analyzer, null, batchSize, BIAS, CLASS, TEXT)
            .assignClass(query);
      }
    }
  }

  private static void assertLearned(
      String message, int batchSize, String[] texts, boolean[] labels, String query)
      throws Exception {
    ClassificationResult<Boolean> r = train(batchSize, texts, labels, query);
    assertEquals(
        message + " (batchSize " + batchSize + ", score " + r.score() + ")",
        Boolean.FALSE,
        r.assignedClass());
  }

  /**
   * "bb" (weight 1) is classified correctly; "aa aa" (weight of aa = 2, output 4 &gt;= 1.5) is
   * misclassified and lowers aa to 0. With batchSize 2 that update sits in the last batch.
   */
  public void testUpdateInLastBatchIsApplied() throws Exception {
    String[] texts = {"bb", "aa aa"};
    boolean[] labels = {false, false};
    assertLearned("control", 1, texts, labels, "aa aa");
    assertLearned("update of 'aa aa' in the last batch was lost", 2, texts, labels, "aa aa");
  }

  /**
   * The same two documents plus "cc cc" labelled true, which is classified correctly. The batch
   * that holds "aa aa" is complete, yet its update is lost; with "cc cc" labelled false it is
   * applied. Whether "aa aa" is learned must not depend on the label of "cc cc".
   */
  public void testUpdateDoesNotDependOnLaterDocument() throws Exception {
    String[] texts = {"bb", "aa aa", "cc cc"};
    assertLearned("control", 1, texts, new boolean[] {false, false, true}, "aa aa");
    assertLearned("'cc cc' labelled false", 2, texts, new boolean[] {false, false, false}, "aa aa");
    assertLearned(
        "'cc cc' labelled true: update of 'aa aa' was lost",
        2,
        texts,
        new boolean[] {false, false, true},
        "aa aa");
  }

  /**
   * Three "aa" documents (weight of aa = 3) are misclassified; with batchSize 3 the FST is rebuilt
   * at the fourth document, but the three decrements of aa keep only one (aa = 2, still &gt;= 1.5).
   */
  public void testUpdatesOfOneTermInOneBatchAddUp() throws Exception {
    String[] texts = {"zz", "aa", "aa", "aa"};
    boolean[] labels = {false, false, false, false};
    assertLearned("control", 1, texts, labels, "aa");
    assertLearned("updates of 'aa' in one batch overwrote each other", 3, texts, labels, "aa");
  }
}
