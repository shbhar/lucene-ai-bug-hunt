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

import java.io.IOException;
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
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * {@link BooleanPerceptronClassifier} must find the weight of every indexed term, whatever
 * characters the term is made of. Its weights FST must therefore be built from the terms in UTF-8
 * byte order (the order {@code FSTCompiler#add} requires), which differs from Java String (UTF-16)
 * order when one term starts with a supplementary character, such as an emoji, and another with a
 * character in U+E000..U+FFFF, such as a fullwidth letter.
 */
public class TestBooleanPerceptronTermOrder extends LuceneTestCase {

  /** "sale" in fullwidth Latin letters, U+FF53 U+FF41 U+FF4C U+FF45 (UTF-8 EF BD 93 ...). */
  private static final String FULLWIDTH_SALE = "\uFF53\uFF41\uFF4C\uFF45";

  /** "sale" in katakana, U+30BB U+30FC U+30EB (UTF-8 E3 82 BB ...), below the surrogates. */
  private static final String KATAKANA_SALE = "\u30BB\u30FC\u30EB";

  /** U+1F389 PARTY POPPER, a supplementary character (UTF-16 D83C DF89, UTF-8 F0 9F 8E 89). */
  private static final String PARTY_POPPER = "\uD83C\uDF89";

  /**
   * Training set {"P P P" -> true, "N" -> false}. The default bias is the average total term
   * frequency, (3 + 1) / 2 = 2. The initial weights are the total term frequencies, P = 3 and N =
   * 1, so both training documents are classified correctly (9 >= 2 and 1 < 2) and no weight
   * changes. assignClass(P) must be (true, 1 - e^-0.5) and assignClass(N) (false, 1 - e^-0.5).
   */
  private static final double EXPECTED_SCORE = 1 - Math.exp(-0.5);

  public void testAsciiTerms() throws IOException {
    checkTrainedOn("sale", "party");
  }

  public void testKatakanaTermAndEmoji() throws IOException {
    checkTrainedOn(KATAKANA_SALE, PARTY_POPPER); // String order and UTF-8 order agree
  }

  public void testFullwidthTermAndEmoji() throws IOException {
    checkTrainedOn(FULLWIDTH_SALE, PARTY_POPPER); // the two orders disagree
  }

  public void testEmojiAndFullwidthTerm() throws IOException {
    checkTrainedOn(PARTY_POPPER, FULLWIDTH_SALE); // the same pair, labels swapped
  }

  private void checkTrainedOn(String p, String n) throws IOException {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        FieldType textType = new FieldType(TextField.TYPE_STORED);
        textType.setStoreTermVectors(true);
        addDocument(writer, textType, p + " " + p + " " + p, true);
        addDocument(writer, textType, n, false);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        BooleanPerceptronClassifier classifier =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, null, "class", "text");
        ClassificationResult<Boolean> positive = classifier.assignClass(p);
        ClassificationResult<Boolean> negative = classifier.assignClass(n);
        String got =
            "assignClass(P) = ("
                + positive.assignedClass()
                + ", "
                + positive.score()
                + "), assignClass(N) = ("
                + negative.assignedClass()
                + ", "
                + negative.score()
                + ")";
        assertTrue(got, positive.assignedClass());
        assertEquals(got, EXPECTED_SCORE, positive.score(), 1e-9);
        assertFalse(got, negative.assignedClass());
        assertEquals(got, EXPECTED_SCORE, negative.score(), 1e-9);
      }
    } finally {
      analyzer.close();
    }
  }

  private static void addDocument(IndexWriter writer, FieldType textType, String text, boolean c)
      throws IOException {
    Document doc = new Document();
    doc.add(new Field("text", text, textType));
    doc.add(new StringField("class", Boolean.toString(c), Field.Store.YES));
    writer.addDocument(doc);
  }
}
