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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.opennlp.tools.OpenNLPOpsFactory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.AttributeFactory;
import org.apache.lucene.util.ClasspathResourceLoader;
import org.junit.BeforeClass;

/**
 * OpenNLPPOSFilter, OpenNLPChunkerFilter and OpenNLPLemmatizerFilter only tag or rewrite terms:
 * each must emit one token per token it receives. Here a StopFilter between the stages removes
 * every token of the FIRST sentence ("Hi."). The sentence-aware filter then emits nothing at all.
 *
 * <p>Oracles (no model of the expected tags needed): (1) token count out == token count in; (2)
 * the terms and types of the surviving sentences are the same whether or not a fully removed
 * sentence precedes them. Controls (expected to pass): the same chains on text whose removed
 * sentence is in the middle, or with no removed sentence.
 *
 * <p>Uses the test models of the analysis/opennlp module (test-files on the classpath).
 */
public class TestDroppedFirstSentenceV1 extends BaseTokenStreamTestCase {

  private static final String SENT = "en-test-sent.bin";
  private static final String TOK = "en-test-tokenizer.bin";
  private static final String POS = "en-test-pos-maxent.bin";
  private static final String CHUNK = "en-test-chunker.bin";
  private static final String DICT = "en-test-lemmas.dict";

  /** Two sentences, none of which the stop filter removes entirely. */
  private static final String REST = "They sent him running in the evening. He did not come back.";

  /** "Hi." is sentence 0 and the stop filter removes all of its tokens. */
  private static final String DROPPED_FIRST = "Hi. " + REST;

  /** Same tokens, but the fully removed sentence is in the middle. */
  private static final String DROPPED_MIDDLE =
      "They sent him running in the evening. Hi. He did not come back.";

  private static final CharArraySet STOP = new CharArraySet(Arrays.asList("hi", "."), true);

  @BeforeClass
  public static void loadModels() throws IOException {
    ClasspathResourceLoader loader = new ClasspathResourceLoader(TestDroppedFirstSentenceV1.class);
    OpenNLPOpsFactory.getSentenceModel(SENT, loader);
    OpenNLPOpsFactory.getTokenizerModel(TOK, loader);
    OpenNLPOpsFactory.getPOSTaggerModel(POS, loader);
    OpenNLPOpsFactory.getChunkerModel(CHUNK, loader);
    OpenNLPOpsFactory.getLemmatizerDictionary(DICT, loader);
  }

  /** OpenNLPTokenizer followed by the given stages: "stop", "pos", "chunk", "lemma". */
  private static Analyzer chain(String... stages) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        try {
          Tokenizer tok =
              new OpenNLPTokenizer(
                  AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY,
                  OpenNLPOpsFactory.getSentenceDetector(SENT),
                  OpenNLPOpsFactory.getTokenizer(TOK));
          TokenStream ts = tok;
          for (String s : stages) {
            switch (s) {
              case "stop":
                ts = new StopFilter(ts, STOP);
                break;
              case "pos":
                ts = new OpenNLPPOSFilter(ts, OpenNLPOpsFactory.getPOSTagger(POS));
                break;
              case "chunk":
                ts = new OpenNLPChunkerFilter(ts, OpenNLPOpsFactory.getChunker(CHUNK));
                break;
              case "lemma":
                ts = new OpenNLPLemmatizerFilter(ts, OpenNLPOpsFactory.getLemmatizer(DICT, null));
                break;
              default:
                throw new IllegalArgumentException(s);
            }
          }
          return new TokenStreamComponents(tok, ts);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  /** Consumes the stream; returns "term/type" per token, plus the final offset as last element. */
  private static List<String> run(Analyzer a, String text, boolean withTypes) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      TypeAttribute type = ts.addAttribute(TypeAttribute.class);
      OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(withTypes ? term + "/" + type.type() : term.toString());
      }
      ts.end();
      out.add("<finalOffset=" + off.endOffset() + ">");
    }
    return out;
  }

  private static List<String> tokens(Analyzer a, String text, boolean withTypes)
      throws IOException {
    List<String> r = run(a, text, withTypes);
    return r.subList(0, r.size() - 1);
  }

  /** The filter under test must pass through every token it receives (same count, same order). */
  private void assertPassThroughCount(String[] before, String filter, String text)
      throws IOException {
    String[] after = Arrays.copyOf(before, before.length + 1);
    after[before.length] = filter;
    List<String> in = tokens(chain(before), text, false);
    List<String> out = tokens(chain(after), text, false);
    System.out.println(
        "chain " + Arrays.toString(after) + " on '" + text + "'\n   in =" + in + "\n   out=" + out);
    assertTrue("sanity: input to " + filter + " must be non-empty", in.size() > 0);
    assertEquals(
        filter + " must emit one token per input token on '" + text + "'", in.size(), out.size());
  }

  // ---- bug cases: the first sentence is removed entirely by an earlier filter ----

  public void testPOSFilterAfterStopFilterRemovingFirstSentence() throws Exception {
    assertPassThroughCount(new String[] {"stop"}, "pos", DROPPED_FIRST);
    // terms are not changed by the POS filter
    assertEquals(
        tokens(chain("stop"), DROPPED_FIRST, false),
        tokens(chain("stop", "pos"), DROPPED_FIRST, false));
    // tags of the surviving sentences do not depend on a removed preceding sentence
    assertEquals(
        tokens(chain("stop", "pos"), REST, true), tokens(chain("stop", "pos"), DROPPED_FIRST, true));
  }

  public void testChunkerFilterAfterStopFilterRemovingFirstSentence() throws Exception {
    assertPassThroughCount(new String[] {"pos", "stop"}, "chunk", DROPPED_FIRST);
    assertEquals(
        tokens(chain("pos", "stop", "chunk"), REST, true),
        tokens(chain("pos", "stop", "chunk"), DROPPED_FIRST, true));
  }

  public void testLemmatizerFilterAfterStopFilterRemovingFirstSentence() throws Exception {
    assertPassThroughCount(new String[] {"pos", "stop"}, "lemma", DROPPED_FIRST);
    assertEquals(
        tokens(chain("pos", "stop", "lemma"), REST, false),
        tokens(chain("pos", "stop", "lemma"), DROPPED_FIRST, false));
  }

  /**
   * Secondary effect: in a field longer than the tokenizer's 1024-char buffer the remaining input is
   * never read, so end() reports a final offset that differs from the one the same chain without
   * the POS filter reports.
   */
  public void testFinalOffsetOfLongFieldWithRemovedFirstSentence() throws Exception {
    StringBuilder sb = new StringBuilder("Hi. ");
    while (sb.length() < 1500) {
      sb.append(REST).append(' ');
    }
    String text = sb.toString();
    List<String> in = run(chain("stop"), text, false);
    List<String> out = run(chain("stop", "pos"), text, false);
    System.out.println(
        "long field: length="
            + text.length()
            + " in.tokens="
            + (in.size() - 1)
            + " "
            + in.get(in.size() - 1)
            + " out.tokens="
            + (out.size() - 1)
            + " "
            + out.get(out.size() - 1));
    assertEquals("final offset", in.get(in.size() - 1), out.get(out.size() - 1));
  }

  // ---- controls: expected to pass on the current code ----

  public void testControlsNoRemovedFirstSentence() throws Exception {
    // no intermediate filter, removed sentence in the middle, or no removed sentence at all
    for (String text : new String[] {DROPPED_FIRST, DROPPED_MIDDLE, REST}) {
      assertPassThroughCount(new String[] {}, "pos", text);
      assertPassThroughCount(new String[] {"pos"}, "chunk", text);
      assertPassThroughCount(new String[] {"pos"}, "lemma", text);
    }
    for (String text : new String[] {DROPPED_MIDDLE, REST}) {
      assertPassThroughCount(new String[] {"stop"}, "pos", text);
      assertPassThroughCount(new String[] {"pos", "stop"}, "chunk", text);
      assertPassThroughCount(new String[] {"pos", "stop"}, "lemma", text);
    }
    System.out.println(
        "lemmas REST: " + tokens(chain("pos", "stop", "lemma"), REST, false));
  }
}
