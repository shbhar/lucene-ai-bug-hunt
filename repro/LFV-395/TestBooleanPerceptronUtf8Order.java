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
 * BooleanPerceptronClassifier keeps its weights in a String-keyed sorted map (UTF-16 code unit order) and feeds
 * them in that order to an FSTCompiler over UTF-8 bytes. The two orders differ when one term starts with a
 * supplementary character (here U+1F600, UTF-8 F0 9F 98 80) and another with a character in U+E000..U+FFFF (here
 * U+FF41 FULLWIDTH LATIN SMALL LETTER A, UTF-8 EF BD 81). With assertions enabled the constructor fails in
 * FSTCompiler ("inputs are added out of order"); with assertions disabled the FST is built from unsorted input and
 * the weight of U+FF41 is lost.
 *
 * <p>Oracle: the classifier only uses term frequencies, so renaming the two tokens to ASCII tokens must not change
 * the classification. The renamed training set classifies its positive-only token as true.
 */
public class TestBooleanPerceptronUtf8Order extends LuceneTestCase {

  private static final String FULLWIDTH_A = "ａ"; // U+FF41, UTF-8 EF BD 81
  private static final String EMOJI = "😀"; // U+1F600, UTF-8 F0 9F 98 80

  private Boolean trainAndClassify(String[] texts, String[] classes, String input) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    FieldType withVectors = new FieldType(TextField.TYPE_STORED);
    withVectors.setStoreTermVectors(true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (int i = 0; i < texts.length; i++) {
          Document doc = new Document();
          doc.add(new Field("text", texts[i], withVectors));
          doc.add(new StringField("cls", classes[i], Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BooleanPerceptronClassifier classifier =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, null, "cls", "text");
        return classifier.assignClass(input).assignedClass();
      }
    }
  }

  public void testSupplementaryAndHighBmpTerms() throws Exception {
    String[] classes = {"true", "false", "false"};
    // control: ASCII tokens, same structure
    Boolean control = trainAndClassify(new String[] {"xa xa xa", "yb", "zz"}, classes, "xa");
    assertEquals("control (ASCII tokens)", Boolean.TRUE, control);
    // same training set with xa -> U+FF41 and yb -> U+1F600
    Boolean actual =
        trainAndClassify(
            new String[] {FULLWIDTH_A + " " + FULLWIDTH_A + " " + FULLWIDTH_A, EMOJI, "zz"},
            classes,
            FULLWIDTH_A);
    assertEquals("renaming tokens must not change the classification", control, actual);
  }
}
