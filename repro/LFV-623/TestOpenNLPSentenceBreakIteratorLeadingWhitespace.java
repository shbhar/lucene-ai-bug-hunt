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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.opennlp.tools.OpenNLPOpsFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;
import org.junit.BeforeClass;

/** OpenNLPSentenceBreakIterator on text that starts with whitespace (two spaces here). */
public class TestOpenNLPSentenceBreakIteratorLeadingWhitespace extends LuceneTestCase {

  private static final String MODEL = "en-test-sent.bin";
  private static final String ONE = "  Sentence number 1 has 6 words.";
  private static final String TWO = ONE + " Sentence number 2, 5 words.";

  @BeforeClass
  public static void loadModel() throws IOException {
    OpenNLPOpsFactory.getSentenceModel(
        MODEL,
        new ClasspathResourceLoader(TestOpenNLPSentenceBreakIteratorLeadingWhitespace.class));
  }

  private static BreakIterator iterator(String text) throws IOException {
    BreakIterator bi =
        new OpenNLPSentenceBreakIterator(OpenNLPOpsFactory.getSentenceDetector(MODEL));
    bi.setText(text);
    return bi;
  }

  private static List<Integer> forward(BreakIterator bi) {
    List<Integer> boundaries = new ArrayList<>();
    for (int b = bi.first(); b != BreakIterator.DONE; b = bi.next()) {
      boundaries.add(b);
    }
    return boundaries;
  }

  /** The "print each element in reverse order" loop of the BreakIterator javadoc. */
  private static List<Integer> backward(BreakIterator bi) {
    List<Integer> boundaries = new ArrayList<>();
    for (int b = bi.last(); b != BreakIterator.DONE; b = bi.previous()) {
      boundaries.add(b);
    }
    return boundaries;
  }

  public void testBackwardWalk() throws IOException {
    BreakIterator control = iterator(TWO.strip()); // no leading whitespace: passes
    assertEquals(List.of(0, 31, 58), forward(control));
    assertEquals(List.of(58, 31, 0), backward(control));

    BreakIterator bi = iterator(TWO);
    assertEquals(List.of(0, 33, 60), forward(bi));
    // actual: 60, 33, 2 (not a boundary), then ArrayIndexOutOfBoundsException: Index -1
    assertEquals(List.of(60, 33, 0), backward(bi));
  }

  public void testFollowingInLeadingWhitespaceOneSentence() throws IOException {
    BreakIterator bi = iterator(ONE);
    int b = bi.following(0); // 0 is the first boundary, a legal offset; actual: StackOverflowError
    // the next boundary is the end of the text; this class returns DONE in the last sentence
    assertTrue("following(0) = " + b, b == ONE.length() || b == BreakIterator.DONE);
  }

  public void testFollowingInLeadingWhitespaceTwoSentences() throws IOException {
    BreakIterator bi = iterator(TWO);
    // actual: AssertionError in moveToSentenceAt with assertions on (33 with assertions off)
    assertEquals(33, bi.following(0));
  }
}
