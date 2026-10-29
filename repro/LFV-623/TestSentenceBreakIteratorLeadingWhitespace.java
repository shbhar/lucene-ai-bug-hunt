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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.opennlp.tools.OpenNLPOpsFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;
import org.junit.BeforeClass;

/**
 * OpenNLPSentenceBreakIterator on a text that starts with whitespace. The sentence detector trims
 * that whitespace, so the first detected sentence starts after the beginning of the text. first()
 * and next() treat the whitespace as part of the first sentence, but the other navigation methods
 * use the trimmed start: previous() returns it (it is not a boundary) and then fails with
 * ArrayIndexOutOfBoundsException, and following() for an offset inside the whitespace recurses
 * until StackOverflowError (one sentence) or fails the binary search's assertions (two or more).
 *
 * <p>Uses the test models of lucene/analysis/opennlp/src/test-files.
 */
public class TestSentenceBreakIteratorLeadingWhitespace extends LuceneTestCase {

  private static final String SENT = "en-test-sent.bin";
  private static final String ONE = "  Sentence number 1 has 6 words.";
  private static final String TWO = "  Sentence number 1 has 6 words. Sentence number 2, 5 words.";

  @BeforeClass
  public static void loadModel() throws Exception {
    OpenNLPOpsFactory.getSentenceModel(
        SENT, new ClasspathResourceLoader(TestSentenceBreakIteratorLeadingWhitespace.class));
  }

  private static BreakIterator newIterator(String text) throws Exception {
    BreakIterator bi = new OpenNLPSentenceBreakIterator(OpenNLPOpsFactory.getSentenceDetector(SENT));
    bi.setText(text);
    return bi;
  }

  /**
   * Walking backward (the reverse-order loop of the BreakIterator class javadoc) must visit the
   * same boundaries as walking forward.
   */
  public void testBackwardWalkMatchesForwardWalk() throws Exception {
    BreakIterator bi = newIterator(TWO);
    List<Integer> forward = new ArrayList<>();
    for (int b = bi.first(); b != BreakIterator.DONE; b = bi.next()) {
      forward.add(b);
    }
    assertEquals(List.of(0, TWO.indexOf("Sentence number 2"), TWO.length()), forward);
    List<Integer> backward = new ArrayList<>();
    for (int b = bi.last(); b != BreakIterator.DONE; b = bi.previous()) {
      backward.add(b);
    }
    Collections.reverse(backward);
    assertEquals(forward, backward);
  }

  /** One sentence after two spaces: following(0) must return a boundary or DONE. */
  public void testFollowingBeforeOnlySentence() throws Exception {
    BreakIterator bi = newIterator(ONE);
    int next = bi.following(0); // legal offset: begin <= 0 <= end
    assertTrue("following(0) = " + next, next == ONE.length() || next == BreakIterator.DONE);
  }

  /** Two sentences after two spaces: the next boundary after offset 0 is the second sentence. */
  public void testFollowingBeforeFirstOfTwoSentences() throws Exception {
    BreakIterator bi = newIterator(TWO);
    assertEquals(TWO.indexOf("Sentence number 2"), bi.following(0));
  }
}
