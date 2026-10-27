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

import java.text.BreakIterator;
import org.apache.lucene.analysis.opennlp.tools.OpenNLPOpsFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;
import org.junit.BeforeClass;

/**
 * OpenNLPSentenceBreakIterator.next(n) must return the n-th boundary from the current one, like n
 * calls of next(). When that boundary is the end of the text it returns DONE instead.
 *
 * <p>Uses the test models of lucene/analysis/opennlp/src/test-files.
 */
public class TestSentenceBreakIteratorNextN extends LuceneTestCase {

  private static final String SENT = "en-test-sent.bin";
  // the text of TestOpenNLPSentenceBreakIterator: boundaries 0, 31, 59, 102
  private static final String TEXT =
      "Sentence number 1 has 6 words. Sentence number 2, 5 words. And finally, sentence number 3 has 8 words.";

  @BeforeClass
  public static void loadModel() throws Exception {
    OpenNLPOpsFactory.getSentenceModel(
        SENT, new ClasspathResourceLoader(TestSentenceBreakIteratorNextN.class));
  }

  private static BreakIterator newIterator(String text) throws Exception {
    BreakIterator bi = new OpenNLPSentenceBreakIterator(OpenNLPOpsFactory.getSentenceDetector(SENT));
    bi.setText(text);
    return bi;
  }

  public void testNextNMatchesRepeatedNext() throws Exception {
    BreakIterator bi = newIterator(TEXT);
    for (int n = 1; n <= 3; n++) {
      bi.first();
      int expected = BreakIterator.DONE;
      for (int i = 0; i < n; i++) {
        expected = bi.next();
      }
      bi.first();
      assertEquals("next(" + n + ") from first()", expected, bi.next(n));
    }
  }

  /** One sentence: next() from first() is the end of the text, so next(1) must be too. */
  public void testNextOneOnSingleSentence() throws Exception {
    String text = "Sentence number 1 has 6 words.";
    BreakIterator bi = newIterator(text);
    bi.first();
    assertEquals(text.length(), bi.next());
    bi.first();
    assertEquals(text.length(), bi.next(1));
  }

  /** Controls that already hold: past the last boundary is DONE; next(-n) to the first works. */
  public void testControls() throws Exception {
    BreakIterator bi = newIterator(TEXT);
    bi.first();
    assertEquals(BreakIterator.DONE, bi.next(4));
    bi.first();
    assertEquals(59, bi.next(2));
    assertEquals(0, bi.next(-2));
  }
}
