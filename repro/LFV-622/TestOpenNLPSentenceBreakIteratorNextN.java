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
package org.apache.lucene.analysis.opennlp;

import java.io.IOException;
import java.text.BreakIterator;
import org.apache.lucene.analysis.opennlp.tools.NLPSentenceDetectorOp;
import org.apache.lucene.analysis.opennlp.tools.OpenNLPOpsFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;
import org.junit.BeforeClass;

/**
 * {@link BreakIterator#next(int)} returns the n-th boundary from the current one, and {@link
 * BreakIterator#next()} is "Equivalent to next(1)". OpenNLPSentenceBreakIterator returns DONE when
 * the n-th boundary is the end of the text.
 */
public class TestOpenNLPSentenceBreakIteratorNextN extends LuceneTestCase {

  private static final String SENTENCE_MODEL = "en-test-sent.bin";
  // the text of TestOpenNLPSentenceBreakIterator; its boundaries are 0, 31, 59 and 102
  private static final String TEXT =
      "Sentence number 1 has 6 words. Sentence number 2, 5 words. And finally, sentence number 3 has 8 words.";

  @BeforeClass
  public static void populateCache() throws IOException {
    OpenNLPOpsFactory.getSentenceModel(
        SENTENCE_MODEL, new ClasspathResourceLoader(TestOpenNLPSentenceBreakIteratorNextN.class));
  }

  /** No model file: the whole text is one sentence, so the boundaries are 0 and 27. */
  public void testNextOneEqualsNextWithoutModel() {
    BreakIterator bi = new OpenNLPSentenceBreakIterator(new NLPSentenceDetectorOp());
    bi.setText("Whole text is one sentence.");
    assertEquals(0, bi.first());
    assertEquals(27, bi.next());
    assertEquals(0, bi.first());
    assertEquals("next(1) from first()", 27, bi.next(1));
  }

  /** The module's test sentence model: next(n) from first() must return boundary n. */
  public void testNextNFromFirstWithModel() throws IOException {
    BreakIterator bi = newModelIterator();
    int[] boundaries = {0, 31, 59, 102};
    for (int n = 1; n < boundaries.length; n++) {
      assertEquals(0, bi.first());
      assertEquals("next(" + n + ") from first()", boundaries[n], bi.next(n));
    }
  }

  /** These already hold: only the returned value is wrong, not the position. */
  public void testControls() throws IOException {
    BreakIterator bi = newModelIterator();
    bi.first();
    assertEquals(BreakIterator.DONE, bi.next(4)); // past the last boundary
    assertEquals(102, bi.current());
    bi.first();
    assertEquals(59, bi.next(2));
    assertEquals(0, bi.next(-2)); // landing on the first boundary returns it
    bi.first();
    bi.next(3); // lands on the end of the text
    assertEquals(102, bi.current()); // the iterator did move to the end
  }

  private static BreakIterator newModelIterator() throws IOException {
    NLPSentenceDetectorOp op = OpenNLPOpsFactory.getSentenceDetector(SENTENCE_MODEL);
    BreakIterator bi = new OpenNLPSentenceBreakIterator(op);
    bi.setText(TEXT);
    return bi;
  }
}
