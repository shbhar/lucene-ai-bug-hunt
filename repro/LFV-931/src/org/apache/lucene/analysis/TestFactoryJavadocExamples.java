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
package org.apache.lucene.analysis;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * Class-javadoc schema examples of analysis factories, copied verbatim (after HTML unescaping) from
 * the javadoc lines cited on each test. Each example should be well-formed XML, and each
 * tokenizer/filter/charFilter element should name a registered factory (a "solr.Name" class
 * resolves by the factory's simple class name, as schema hosts resolve it) that accepts the
 * example's attributes.
 */
public class TestFactoryJavadocExamples extends LuceneTestCase {

  private static void assertWellFormed(String where, String xml) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    DocumentBuilder db = dbf.newDocumentBuilder();
    db.setErrorHandler(
        new ErrorHandler() {
          @Override
          public void warning(SAXParseException e) {}

          @Override
          public void error(SAXParseException e) throws SAXParseException {
            throw e;
          }

          @Override
          public void fatalError(SAXParseException e) throws SAXParseException {
            throw e;
          }
        });
    try {
      db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    } catch (SAXParseException e) {
      throw new AssertionError(
          where + ": example is not well-formed XML (line " + e.getLineNumber() + "): " + e.getMessage());
    }
  }

  /** Resolves "solr.SimpleName" the way schema hosts do and builds it with the attributes. */
  private static void assertNamesFactory(String where, String kind, String cls, String... kv) {
    Map<String, String> args = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      args.put(kv[i], kv[i + 1]);
    }
    String simple = cls.substring(cls.lastIndexOf('.') + 1);
    java.util.Set<String> names =
        switch (kind) {
          case "tokenizer" -> TokenizerFactory.availableTokenizers();
          case "filter" -> TokenFilterFactory.availableTokenFilters();
          default -> CharFilterFactory.availableCharFilters();
        };
    for (String spi : names) {
      Class<?> c =
          switch (kind) {
            case "tokenizer" -> TokenizerFactory.lookupClass(spi);
            case "filter" -> TokenFilterFactory.lookupClass(spi);
            default -> CharFilterFactory.lookupClass(spi);
          };
      if (c.getSimpleName().equals(simple) && cls.startsWith("solr.")) {
        switch (kind) {
          case "tokenizer" -> TokenizerFactory.forName(spi, args);
          case "filter" -> TokenFilterFactory.forName(spi, args);
          default -> CharFilterFactory.forName(spi, args);
        }
        return;
      }
    }
    fail(where + ": <" + kind + " class=\"" + cls + "\"> names no registered " + kind + " factory");
  }

  /** Control: a correct example (LimitTokenPositionFilterFactory.java:27-33) passes both checks. */
  public void testControlCorrectExample() throws Exception {
    assertWellFormed(
        "LimitTokenPositionFilterFactory",
        "<fieldType name=\"text_limit_pos\" class=\"solr.TextField\" positionIncrementGap=\"100\">\n"
            + "  <analyzer>\n"
            + "    <tokenizer class=\"solr.WhitespaceTokenizerFactory\"/>\n"
            + "    <filter class=\"solr.LimitTokenPositionFilterFactory\" maxTokenPosition=\"3\" consumeAllTokens=\"false\" />\n"
            + "  </analyzer>\n"
            + "</fieldType>");
    assertNamesFactory(
        "LimitTokenPositionFilterFactory", "filter", "solr.LimitTokenPositionFilterFactory",
        "maxTokenPosition", "3", "consumeAllTokens", "false");
  }

  /** LimitTokenOffsetFilterFactory.java:30 names the filter class, not its factory. */
  public void testLimitTokenOffsetExample() {
    assertNamesFactory(
        "LimitTokenOffsetFilterFactory.java:30", "filter", "solr.LimitTokenOffsetFilter",
        "maxStartOffset", "100000", "consumeAllTokens", "false");
  }

  /** PatternTypingFilterFactory.java:40 names com.example.PatternTypingFilter for this factory. */
  public void testPatternTypingExampleOwnElement() {
    assertNamesFactory(
        "PatternTypingFilterFactory.java:40", "filter", "com.example.PatternTypingFilter",
        "patternFile", "patterns.txt");
  }

  /** PatternTypingFilterFactory.java:41 names solr.TokenAnalyzerFilter. */
  public void testPatternTypingExampleSecondElement() {
    assertNamesFactory(
        "PatternTypingFilterFactory.java:41", "filter", "solr.TokenAnalyzerFilter",
        "asType", "text_en", "preserveType", "true");
  }

  /** JapaneseIterationMarkCharFilterFactory.java:29: the class attribute has no closing quote. */
  public void testJapaneseIterationMarkExample() throws Exception {
    assertWellFormed(
        "JapaneseIterationMarkCharFilterFactory.java:27-32",
        "<fieldType name=\"text_ja\" class=\"solr.TextField\" positionIncrementGap=\"100\" autoGeneratePhraseQueries=\"false\">\n"
            + "  <analyzer>\n"
            + "    <charFilter class=\"solr.JapaneseIterationMarkCharFilterFactory normalizeKanji=\"true\" normalizeKana=\"true\"/>\n"
            + "    <tokenizer class=\"solr.JapaneseTokenizerFactory\"/>\n"
            + "  </analyzer>\n"
            + "</fieldType>");
  }

  /** StopFilterFactory.java:33-34: the filter element is never closed. */
  public void testStopFilterExample() throws Exception {
    assertWellFormed(
        "StopFilterFactory.java:30-36",
        "<fieldType name=\"text_stop\" class=\"solr.TextField\" positionIncrementGap=\"100\" autoGeneratePhraseQueries=\"true\">\n"
            + "  <analyzer>\n"
            + "    <tokenizer class=\"solr.WhitespaceTokenizerFactory\"/>\n"
            + "    <filter class=\"solr.StopFilterFactory\" ignoreCase=\"true\"\n"
            + "            words=\"stopwords.txt\" format=\"wordset\"\n"
            + "  </analyzer>\n"
            + "</fieldType>");
  }

  /** BeiderMorseFilterFactory.java:35-38: the filter start tag has no '>' before its end tag. */
  public void testBeiderMorseExample() throws Exception {
    assertWellFormed(
        "BeiderMorseFilterFactory.java:32-40",
        "<fieldType name=\"text_bm\" class=\"solr.TextField\" positionIncrementGap=\"100\">\n"
            + "  <analyzer>\n"
            + "    <tokenizer class=\"solr.StandardTokenizerFactory\"/>\n"
            + "    <filter class=\"solr.BeiderMorseFilterFactory\"\n"
            + "       nameType=\"GENERIC\" ruleType=\"APPROX\"\n"
            + "       concat=\"true\" languageSet=\"auto\"\n"
            + "    </filter>\n"
            + "  </analyzer>\n"
            + "</fieldType>");
  }

  /** OpenNLPTokenizerFactory.java:34: the fieldType start tag has no '>'. */
  public void testOpenNLPTokenizerExample() throws Exception {
    assertWellFormed(
        "OpenNLPTokenizerFactory.java:34-38",
        "<fieldType name=\"text_opennlp\" class=\"solr.TextField\" positionIncrementGap=\"100\"\n"
            + "  <analyzer>\n"
            + "    <tokenizer class=\"solr.OpenNLPTokenizerFactory\" sentenceModel=\"filename\" tokenizerModel=\"filename\"/>\n"
            + "  </analyzer>\n"
            + "</fieldType>");
  }

  /** OpenNLPLemmatizerFilterFactory.java:33 and :38: no '>' on fieldType, and a stray "/>". */
  public void testOpenNLPLemmatizerExample() throws Exception {
    assertWellFormed(
        "OpenNLPLemmatizerFilterFactory.java:33-43",
        "<fieldType name=\"text_opennlp_lemma\" class=\"solr.TextField\" positionIncrementGap=\"100\"\n"
            + "  <analyzer>\n"
            + "    <tokenizer class=\"solr.OpenNLPTokenizerFactory\"\n"
            + "               sentenceModel=\"filename\"\n"
            + "               tokenizerModel=\"filename\"/>\n"
            + "    />\n"
            + "    <filter class=\"solr.OpenNLPLemmatizerFilterFactory\"\n"
            + "            dictionary=\"filename\"\n"
            + "            lemmatizerModel=\"filename\"/>\n"
            + "  </analyzer>\n"
            + "</fieldType>");
  }

  /** DropIfFlaggedFilterFactory.java:35: "<-- ... -->" is not an XML comment. */
  public void testDropIfFlaggedExample() throws Exception {
    assertWellFormed(
        "DropIfFlaggedFilterFactory.java:33-38",
        "<analyzer type=\"index\">\n"
            + "  <tokenizer class=\"solr.WhitespaceTokenizerFactory\"/>\n"
            + "  <-- other filters -->\n"
            + "  <filter class=\"solr.DropIfFlaggedFilterFactory\" dropFlags=\"9\"/>\n"
            + "</analyzer>");
  }
}
