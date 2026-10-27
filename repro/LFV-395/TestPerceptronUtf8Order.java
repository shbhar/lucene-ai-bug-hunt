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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent probe: BooleanPerceptronClassifier with the stock StandardAnalyzer, an explicit bias
 * and a two-document corpus. Weights start as each term's total term frequency, so with bias 2 the
 * term that occurs 3 times must classify as true and the term that occurs once as false; both
 * training documents are classified correctly up front, so no weight update happens.
 */
public class TestPerceptronUtf8Order extends LuceneTestCase {

  private static Boolean classify(String positiveDoc, String negativeDoc, String input)
      throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    FieldType withVectors = new FieldType(TextField.TYPE_STORED);
    withVectors.setStoreTermVectors(true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        String[] texts = {positiveDoc, negativeDoc};
        String[] classes = {"true", "false"};
        for (int i = 0; i < texts.length; i++) {
          Document doc = new Document();
          doc.add(new Field("text", texts[i], withVectors));
          doc.add(new StringField("cls", classes[i], Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BooleanPerceptronClassifier classifier =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, 2d, "cls", "text");
        return classifier.assignClass(input).assignedClass();
      } finally {
        analyzer.close();
      }
    }
  }

  /** ASCII control. */
  public void testAsciiControl() throws Exception {
    assertEquals(Boolean.TRUE, classify("sale sale sale", "party", "sale"));
  }

  /**
   * Control: emoji plus CJK unified ideographs (below U+D800). UTF-16 and UTF-8 orders agree, so
   * this must pass on stock Lucene.
   */
  public void testEmojiWithCjkIdeographsControl() throws Exception {
    // U+5927 (UTF-8 E5 A4 A7); StandardTokenizer emits one token per ideograph
    assertEquals(Boolean.TRUE, classify("大 大 大", "🎉", "大"));
  }

  /**
   * Halfwidth katakana (U+FF7E U+FF70 U+FF99, UTF-8 EF BD BE ...) and an emoji (U+1F389, UTF-8 F0
   * 9F 8E 89), ordinary Japanese user-generated text through the stock StandardAnalyzer.
   */
  public void testHalfwidthKatakanaWithEmoji() throws Exception {
    String sale = "ｾｰﾙ";
    String party = "🎉";
    assertEquals(Boolean.TRUE, classify(sale + " " + sale + " " + sale, party, sale));
  }

  /** Fullwidth Latin (U+FF53 U+FF41 U+FF4C U+FF45 "ｓａｌｅ") and an emoji. */
  public void testFullwidthLatinWithEmoji() throws Exception {
    String sale = "ｓａｌｅ";
    String party = "🎉";
    assertEquals(Boolean.TRUE, classify(sale + " " + sale + " " + sale, party, sale));
  }
}
