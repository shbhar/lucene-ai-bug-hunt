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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import opennlp.tools.util.Span;
import org.apache.lucene.analysis.opennlp.tools.OpenNLPOpsFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;
import org.junit.BeforeClass;

/**
 * OpenNLPSentenceBreakIterator on text whose first characters are whitespace.
 *
 * <p>first() returns the begin index and next() then returns the start of the second detected
 * sentence, so the boundaries of "\n\tS1 S2" are {0, start(S2), end}. The sentence detector's spans
 * are whitespace-trimmed (the first span starts after the "\n\t"), and previous(), following(),
 * preceding() and next(-n) use that trimmed start. Each check below also runs on the same text
 * without the leading whitespace (testControlNoLeadingWhitespace), where it passes.
 *
 * <p>Uses only public API plus the test sentence model of the opennlp module (en-test-sent.bin).
 */
public class TestSentenceBreakIteratorLeadingBlank extends LuceneTestCase {

  private static final String MODEL = "en-test-sent.bin";
  private static final String LEAD = "\n\t";
  // the test model splits these two sentences (they are the first two of the upstream test)
  private static final String S1 = "Sentence number 1 has 6 words. ";
  private static final String S2 = "Sentence number 2, 5 words.";

  @BeforeClass
  public static void loadModel() throws IOException {
    OpenNLPOpsFactory.getSentenceModel(
        MODEL, new ClasspathResourceLoader(TestSentenceBreakIteratorLeadingBlank.class));
  }

  private static BreakIterator iterator(String text) throws IOException {
    BreakIterator bi =
        new OpenNLPSentenceBreakIterator(OpenNLPOpsFactory.getSentenceDetector(MODEL));
    bi.setText(text);
    return bi;
  }

  /** Precondition: the detector finds the expected sentences and trims the leading whitespace. */
  private static void assertSpanStarts(String text, int... expectedStarts) throws IOException {
    Span[] spans = OpenNLPOpsFactory.getSentenceDetector(MODEL).splitSentences(text);
    int[] starts = new int[spans.length];
    for (int i = 0; i < spans.length; i++) {
      starts[i] = spans[i].getStart();
    }
    assertArrayEquals(
        "detector span starts " + Arrays.toString(starts), expectedStarts, starts);
  }

  private static List<Integer> forwardWalk(BreakIterator bi) {
    List<Integer> b = new ArrayList<>();
    for (int p = bi.first(); p != BreakIterator.DONE; p = bi.next()) {
      b.add(p);
      assertTrue("forward walk does not terminate: " + b, b.size() < 50);
    }
    return b;
  }

  private static AssertionError threw(String call, Throwable t) {
    StackTraceElement top = t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null;
    return new AssertionError(call + " threw " + t.getClass().getName() + " at " + top);
  }

  // ---------------------------------------------------------------- checks

  /** last()/previous() must visit the forward boundaries in reverse order, then return DONE. */
  private void checkBackwardWalk(String lead) throws IOException {
    String text = lead + S1 + S2;
    assertSpanStarts(text, lead.length(), lead.length() + S1.length());
    BreakIterator bi = iterator(text);
    List<Integer> fwd = forwardWalk(bi);
    assertEquals(List.of(0, lead.length() + S1.length(), text.length()), fwd);
    List<Integer> bwd = new ArrayList<>();
    try {
      for (int p = bi.last(); p != BreakIterator.DONE; p = bi.previous()) {
        bwd.add(p);
        assertTrue("backward walk does not terminate: " + bwd, bwd.size() < 50);
      }
    } catch (RuntimeException e) {
      throw threw("backward walk " + bwd + ", then previous()", e);
    }
    Collections.reverse(bwd);
    assertEquals("backward walk (reversed) vs forward walk", fwd, bwd);
  }

  /** following(offset) in the first sentence of a one-sentence text: DONE or the end index. */
  private void checkFollowingOneSentence(String lead) throws IOException {
    String text = lead + "Sentence number 1 has 6 words.";
    assertSpanStarts(text, lead.length());
    BreakIterator bi = iterator(text);
    int r;
    try {
      r = bi.following(0);
    } catch (Throwable t) {
      throw threw("following(0) on a one-sentence text", t);
    }
    // java.text.BreakIterator: the first boundary after 0 (= end). The class returns DONE for any
    // offset in the last sentence (its JDK-8015110 note); accept either.
    assertTrue(
        "following(0) = " + r, r == text.length() || r == BreakIterator.DONE);
  }

  /** following(0) in a two-sentence text must return the second boundary of the forward walk. */
  private void checkFollowingTwoSentences(String lead) throws IOException {
    String text = lead + S1 + S2;
    assertSpanStarts(text, lead.length(), lead.length() + S1.length());
    BreakIterator bi = iterator(text);
    int expected = forwardWalk(bi).get(1);
    int r;
    try {
      r = bi.following(0);
    } catch (Throwable t) {
      throw threw("following(0) on a two-sentence text", t);
    }
    assertEquals("following(0)", expected, r);
  }

  /** preceding(offset) must return a boundary (javadoc: "the last boundary preceding") or DONE. */
  private void checkPrecedingReturnsBoundary(String lead) throws IOException {
    String text = lead + S1 + S2;
    BreakIterator bi = iterator(text);
    List<Integer> fwd = forwardWalk(bi);
    int offset = lead.length() + S1.length() + 5; // inside the second sentence
    int r = bi.preceding(offset);
    assertTrue(
        "preceding(" + offset + ") = " + r + " is not one of the boundaries " + fwd,
        r == BreakIterator.DONE || fwd.contains(r));
  }

  /** From last(), next(-2) must equal two previous() steps from last() (here: the begin index). */
  private void checkNextMinusTwoFromLast(String lead) throws IOException {
    String text = lead + S1 + S2;
    BreakIterator bi = iterator(text);
    List<Integer> fwd = forwardWalk(bi);
    bi.last();
    assertEquals("next(-2) from last()", (int) fwd.get(0), bi.next(-2));
  }

  // ---------------------------------------------------------------- tests

  public void testControlNoLeadingWhitespace() throws IOException {
    checkBackwardWalk("");
    checkFollowingOneSentence("");
    checkFollowingTwoSentences("");
    checkPrecedingReturnsBoundary("");
    checkNextMinusTwoFromLast("");
  }

  public void testBackwardWalk() throws IOException {
    checkBackwardWalk(LEAD);
  }

  public void testFollowingOneSentence() throws IOException {
    checkFollowingOneSentence(LEAD);
  }

  public void testFollowingTwoSentences() throws IOException {
    checkFollowingTwoSentences(LEAD);
  }

  public void testPrecedingReturnsBoundary() throws IOException {
    checkPrecedingReturnsBoundary(LEAD);
  }

  public void testNextMinusTwoFromLast() throws IOException {
    checkNextMinusTwoFromLast(LEAD);
  }
}
