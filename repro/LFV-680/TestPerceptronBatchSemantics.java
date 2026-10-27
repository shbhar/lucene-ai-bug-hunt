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
 * V3 probe (intended-behaviour check). It asserts the CURRENT stock behaviour, so it passes on an
 * unpatched build, and it tests two defences:
 *
 * <p>(1) "batchSize is only a lazy FST refresh, a deliberate staleness trade-off": then whether a
 * document's update reaches the model should not depend on the label of an unrelated document
 * that comes later. It does.
 *
 * <p>(2) "within a batch, updates are computed against the snapshot, as in mini-batch learning":
 * mini-batch learning sums the per-document deltas. Here a flush does happen, but the flushed
 * weight holds only one delta although three misclassified documents updated the same term.
 */
public class TestPerceptronBatchSemantics extends LuceneTestCase {

  private static final String TEXT = "text";
  private static final String CLASS = "cls";

  private static FieldType textType() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(true);
    ft.freeze();
    return ft;
  }

  private static Boolean train(int batchSize, String query, String[] texts, boolean[] labels)
      throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    FieldType ft = textType();
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
        assertEquals("single segment expected", 1, reader.leaves().size());
        BooleanPerceptronClassifier c =
            new BooleanPerceptronClassifier(reader, analyzer, null, batchSize, 1.5, CLASS, TEXT);
        ClassificationResult<Boolean> r = c.assignClass(query);
        System.out.println(
            "V3 batchSize="
                + batchSize
                + " docs="
                + java.util.Arrays.toString(texts)
                + " labels="
                + java.util.Arrays.toString(labels)
                + " assignClass('"
                + query
                + "')="
                + r.assignedClass()
                + " score="
                + r.score());
        return r.assignedClass();
      }
    }
  }

  /** (1) The update of "aa aa" is applied or lost depending on the label of the unrelated "cc cc". */
  public void testUpdateSurvivalDependsOnUnrelatedLaterDocument() throws Exception {
    // A: no third document: the update of "aa aa" (position 1) is lost.
    assertEquals(
        Boolean.TRUE,
        train(2, "aa aa", new String[] {"bb", "aa aa"}, new boolean[] {false, false}));
    // B: third document "cc cc" labelled false is misclassified at position 2 (a batch boundary):
    // its rebuild also flushes the pending update of "aa aa".
    assertEquals(
        Boolean.FALSE,
        train(
            2,
            "aa aa",
            new String[] {"bb", "aa aa", "cc cc"},
            new boolean[] {false, false, false}));
    // C: the same third document labelled true is classified correctly: no rebuild, and the
    // update of "aa aa" is lost again.
    assertEquals(
        Boolean.TRUE,
        train(
            2,
            "aa aa",
            new String[] {"bb", "aa aa", "cc cc"},
            new boolean[] {false, false, true}));
    // Control: batchSize 1 learns "aa aa" in all three training sets.
    assertEquals(
        Boolean.FALSE,
        train(1, "aa aa", new String[] {"bb", "aa aa"}, new boolean[] {false, false}));
    assertEquals(
        Boolean.FALSE,
        train(
            1,
            "aa aa",
            new String[] {"bb", "aa aa", "cc cc"},
            new boolean[] {false, false, true}));
  }

  /**
   * (2) batchSize 3, docs "zz"(F) "aa"(F) "aa"(F) "aa"(F), bias 1.5, initial weight aa=3. The
   * three "aa" documents are all misclassified against the snapshot (aa=3) and the FST IS rebuilt
   * at position 3, yet the result keeps only one delta: aa=2, so "aa" is still true. Summing the
   * deltas (mini-batch: 3-1-1=1 after the first batch) or learning online (3->2->1) both give
   * false.
   */
  public void testInBatchUpdatesOverwriteInsteadOfAccumulate() throws Exception {
    String[] texts = {"zz", "aa", "aa", "aa"};
    boolean[] labels = {false, false, false, false};
    assertEquals(Boolean.TRUE, train(3, "aa", texts, labels));
    assertEquals(Boolean.FALSE, train(1, "aa", texts, labels));
  }
}
