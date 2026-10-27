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
import java.util.ArrayList;
import java.util.List;
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
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: BooleanPerceptronClassifier keeps its weights in a String-keyed sorted map
 * (UTF-16 code unit order) and feeds them, in that order, to FSTCompiler.add, whose javadoc
 * requires inputs sorted by IntsRef#compareTo (UTF-8 byte order for a BYTE1 FST built from UTF-8
 * terms). The two orders disagree when one term starts with a supplementary character and another
 * with a character in U+E000..U+FFFF.
 *
 * <p>Corpus: {"P P P" -> true, "N" -> false}. Expected values follow from the algorithm: bias =
 * sumTotalTermFreq / docCount = 4 / 2 = 2.0; initial weights P = 3, N = 1; both training docs are
 * classified correctly (outputs 9 and 1), so no weight update happens. Hence assignClass(P) has
 * output 3 (class true) and assignClass(N) has output 1 (class false), and both have score 1 -
 * exp(-|2 - 3| / 2) = 1 - exp(-|2 - 1| / 2) = 1 - exp(-0.5). Only term identity and term
 * frequencies enter the computation, so the result must not depend on which characters P and N
 * are.
 */
public class TestPerceptronUtf8InputOrderV1 extends LuceneTestCase {

  /** U+1D400 MATHEMATICAL BOLD CAPITAL A: UTF-16 D835 DC00, UTF-8 F0 9D 90 80. */
  private static final String SUPPLEMENTARY = new String(Character.toChars(0x1D400));

  /** U+F900 CJK COMPATIBILITY IDEOGRAPH-F900: UTF-16 F900, UTF-8 EF A4 80. */
  private static final String HIGH_BMP = "\uF900";

  /** U+4E00 CJK UNIFIED IDEOGRAPH-4E00: UTF-16 4E00, UTF-8 E4 B8 80 (below the surrogates). */
  private static final String LOW_BMP = "\u4E00";

  private static final String TEXT = "text";
  private static final String CLASS = "cls";
  private static final double EXPECTED_SCORE = 1 - Math.exp(-0.5);

  private static boolean cls(ClassificationResult<Boolean> r) {
    return r.assignedClass();
  }

  private static double score(ClassificationResult<Boolean> r) {
    return r.score();
  }

  private static void addDoc(IndexWriter w, FieldType ft, String text, boolean c)
      throws IOException {
    Document doc = new Document();
    doc.add(new Field(TEXT, text, ft));
    doc.add(new StringField(CLASS, Boolean.toString(c), Field.Store.YES));
    w.addDocument(doc);
  }

  /** Trains on {"P P P" -> true, "N" -> false}; returns [assignClass(P), assignClass(N)]. */
  private List<ClassificationResult<Boolean>> trainAndClassify(String p, String n)
      throws IOException {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        FieldType ft = new FieldType(TextField.TYPE_STORED);
        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorPositions(true);
        ft.setStoreTermVectorOffsets(true);
        addDoc(w, ft, p + " " + p + " " + p, true);
        addDoc(w, ft, n, false);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        // Precondition: the text field holds exactly the terms P and N, with the expected freqs.
        TermsEnum te = MultiTerms.getTerms(reader, TEXT).iterator();
        List<String> terms = new ArrayList<>();
        BytesRef t;
        while ((t = te.next()) != null) {
          terms.add(t.utf8ToString() + ":" + te.totalTermFreq());
        }
        assertEquals(2, terms.size());
        assertTrue(terms.toString(), terms.contains(p + ":3") && terms.contains(n + ":1"));

        BooleanPerceptronClassifier classifier =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, null, CLASS, TEXT);
        List<ClassificationResult<Boolean>> out = new ArrayList<>();
        out.add(classifier.assignClass(p));
        out.add(classifier.assignClass(n));
        return out;
      }
    } finally {
      analyzer.close();
    }
  }

  private void check(String p, String n) throws IOException {
    String orders =
        "String order P<N="
            + (p.compareTo(n) < 0)
            + ", UTF-8 order P<N="
            + (new BytesRef(p).compareTo(new BytesRef(n)) < 0);
    List<ClassificationResult<Boolean>> r = trainAndClassify(p, n);
    String got =
        "assignClass(P)=("
            + cls(r.get(0))
            + ", "
            + score(r.get(0))
            + ") assignClass(N)=("
            + cls(r.get(1))
            + ", "
            + score(r.get(1))
            + ") expected (true, "
            + EXPECTED_SCORE
            + ") / (false, "
            + EXPECTED_SCORE
            + "); "
            + orders;
    assertTrue(got, cls(r.get(0)));
    assertEquals(got, EXPECTED_SCORE, score(r.get(0)), 1e-12);
    assertFalse(got, cls(r.get(1)));
    assertEquals(got, EXPECTED_SCORE, score(r.get(1)), 1e-12);
  }

  /** Control: ASCII terms. */
  public void testAsciiControl() throws IOException {
    check("pa", "nb");
  }

  /** Control: BMP term below the surrogates plus a supplementary term (both orders agree). */
  public void testLowBmpAndSupplementaryControl() throws IOException {
    assertTrue(LOW_BMP.compareTo(SUPPLEMENTARY) < 0);
    assertTrue(new BytesRef(LOW_BMP).compareTo(new BytesRef(SUPPLEMENTARY)) < 0);
    check(LOW_BMP, SUPPLEMENTARY);
  }

  /** Trigger: P in U+E000..U+FFFF, N supplementary (the orders disagree). */
  public void testHighBmpPositiveSupplementaryNegative() throws IOException {
    assertTrue(SUPPLEMENTARY.compareTo(HIGH_BMP) < 0); // UTF-16: D835 < F900
    assertTrue(new BytesRef(SUPPLEMENTARY).compareTo(new BytesRef(HIGH_BMP)) > 0); // F0 > EF
    check(HIGH_BMP, SUPPLEMENTARY);
  }

  /** Trigger, roles swapped: P supplementary, N in U+E000..U+FFFF. */
  public void testSupplementaryPositiveHighBmpNegative() throws IOException {
    check(SUPPLEMENTARY, HIGH_BMP);
  }
}
