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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BooleanPerceptronClassifier with batchSize &gt; 1: weight updates computed for misclassified
 * training documents never reach the model.
 *
 * <p>Model of a training run (read from the class): the initial weight of a term is its
 * totalTermFreq; documents are visited in doc id order; a document is classified true iff the sum
 * of its tokens' weights is &gt;= bias; a misclassified document changes the weight of each of its
 * terms by +/- its in-document term frequency (clamped at 0).
 *
 * <p>Expected values below are computed by hand for two references: the online perceptron
 * (batchSize 1, which the code implements) and a mini-batch perceptron that applies the summed
 * updates of a batch when the batch ends. Both references agree on every assertion.
 */
public class TestPerceptronBatchV1 extends LuceneTestCase {

  static final String TEXT = "text";
  static final String CLS = "cls";

  static IndexReader index(Directory dir, Analyzer analyzer, String[][] docs) throws Exception {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(true);
    ft.freeze();
    // plain config: a single segment and doc ids in insertion order
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new Field(TEXT, d[0], ft));
        doc.add(new StringField(CLS, d[1], Field.Store.YES));
        w.addDocument(doc);
      }
      w.commit();
    }
    IndexReader r = DirectoryReader.open(dir);
    assertEquals(1, r.leaves().size());
    return r;
  }

  static ClassificationResult<Boolean> train(
      String[][] docs, int batchSize, double bias, String probe) throws Exception {
    try (Directory dir = newDirectory()) {
      Analyzer analyzer = new MockAnalyzer(random());
      try (IndexReader r = index(dir, analyzer, docs)) {
        BooleanPerceptronClassifier c =
            new BooleanPerceptronClassifier(r, analyzer, null, batchSize, bias, CLS, TEXT);
        ClassificationResult<Boolean> res = c.assignClass(probe);
        System.out.println(
            "batchSize="
                + batchSize
                + " probe='"
                + probe
                + "' -> "
                + res.assignedClass()
                + " score="
                + res.score());
        return res;
      } finally {
        analyzer.close();
      }
    }
  }

  /**
   * One complete batch of two documents, both misclassified by the initial weights. The update of
   * the document at batch position 0 is applied, the update of the one at position 1 is dropped.
   *
   * <pre>
   * initial: apple=1 berry=3, bias 2
   * d0 "apple"->true : 1 &lt; 2 -> false, wrong -> apple=2 (position 0: FST rebuilt)
   * d1 "berry berry berry"->false : 9 >= 2 -> true, wrong -> berry=0 (position 1: not rebuilt)
   * online and mini-batch reference: apple=2 berry=0 -> probe "berry berry berry" = 0 -> false
   * </pre>
   */
  public void testSecondUpdateOfCompleteBatchDropped() throws Exception {
    String[][] docs = {{"apple", "true"}, {"berry berry berry", "false"}};
    // position-0 update was applied (control inside the same run)
    assertTrue(
        "update of batch position 0 should be applied",
        train(docs, 2, 2.0, "apple").assignedClass());
    ClassificationResult<Boolean> r = train(docs, 2, 2.0, "berry berry berry");
    assertFalse(
        "batchSize=2: the update of the misclassified document at batch position 1 was dropped"
            + " (score "
            + r.score()
            + " equals the untrained model's 1-exp(-3.5))",
        r.assignedClass());
  }

  /**
   * A pending update stays unapplied even though several complete batches follow it, because the
   * FST is only rebuilt when a later misclassified document happens to sit at a position that is a
   * multiple of batchSize. Not an end-of-training (partial last batch) effect.
   *
   * <pre>
   * initial: cherry=5 date=3, bias 2
   * d0 "cherry"->true correct; d1 "date date date"->false : 9 -> true, wrong -> date=0 pending
   * d2..d5 "cherry"->true correct (batches {d2,d3} and {d4,d5} complete, no rebuild)
   * online and mini-batch reference: date=0 -> probe = 0 -> false
   * </pre>
   */
  public void testPendingUpdateNeverAppliedAcrossLaterCompleteBatches() throws Exception {
    String[][] docs = {
      {"cherry", "true"},
      {"date date date", "false"},
      {"cherry", "true"},
      {"cherry", "true"},
      {"cherry", "true"},
      {"cherry", "true"}
    };
    ClassificationResult<Boolean> r = train(docs, 2, 2.0, "date date date");
    assertFalse(
        "batchSize=2: the update of 'date date date' was never applied although two more"
            + " complete batches were trained",
        r.assignedClass());
  }

  /**
   * Isolates the in-batch overwrite: two misclassified documents of one batch update the same term,
   * and the batch IS flushed afterwards (by a misclassified document at the next batch's position
   * 0). Each update is computed from the last FST build and overwrites the pending value, so only
   * one of the two decrements survives.
   *
   * <pre>
   * initial: t=6 u=3, bias 4.5, batchSize 3
   * d0 "t t t t"->true : 24 -> true, correct
   * d1 "t"->false : 6 -> true, wrong -> pending t = 6-1 = 5
   * d2 "t"->false : FST still t=6 -> true, wrong -> pending t = 6-1 = 5 (overwrites; should be 4)
   * d3 "u u u"->false : 9 -> true, wrong -> u=0, position 3: FST rebuilt with t=5 u=0
   * online:     d1 -> t=5; d2: 5 >= 4.5 still wrong -> t=4; d3 -> u=0
   * mini-batch: batch {d0,d1,d2} from t=6: t = 6-1-1 = 4; batch {d3}: u=0
   * both references: probe "t" = 4 &lt; 4.5 -> false; probe "u u u" = 0 -> false
   * </pre>
   */
  public void testTwoUpdatesOfSameTermInOneBatchKeepOnlyOne() throws Exception {
    String[][] docs = {{"t t t t", "true"}, {"t", "false"}, {"t", "false"}, {"u u u", "false"}};
    // the flush did happen: d3's update is visible
    assertFalse("flush at position 3 expected", train(docs, 3, 4.5, "u u u").assignedClass());
    ClassificationResult<Boolean> r = train(docs, 3, 4.5, "t");
    assertFalse(
        "batchSize=3: two updates of term 't' in one batch kept only one (t=5 instead of 4)",
        r.assignedClass());
  }
}
