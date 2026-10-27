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
package org.apache.lucene.monitor;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RegexpQueryHandler indexes a regexp query under its longest literal plus a suffix, and generates
 * every substring of each short document token plus the same suffix, so that the presearcher
 * selects the query. A token that contains a supplementary character (two UTF-16 chars) must get
 * its substrings like any other short token.
 */
public class TestRegexpQueryHandlerSupplementaryChars extends LuceneTestCase {

  private static final String FIELD = "text";
  private static final String EMOJI = Character.toString(0x1F600); // GRINNING FACE
  private static final String ADLAM_ALIF = Character.toString(0x1E922); // ADLAM SMALL LETTER ALIF
  private static final String ADLAM_WORD =
      ADLAM_ALIF + Character.toString(0x1E926) + Character.toString(0x1E928);

  /** The document-side ngrams of a short token. */
  public void testShortTokenIsNgrammed() throws IOException {
    // control: two BMP characters
    assertEquals(Set.of("ab", "abXX", "aXX", "bXX", "XX"), ngrams("ab"));
    // the same shape, with a supplementary second character
    String token = "a" + EMOJI;
    assertEquals(Set.of(token, token + "XX", "aXX", EMOJI + "XX", "XX"), ngrams(token));
  }

  public void testWhitespaceAnalyzer() throws IOException {
    try (Analyzer analyzer = new WhitespaceAnalyzer()) {
      assertReported(analyzer, "hel.*", "hello"); // control
      assertReported(analyzer, "hel.*", "hello" + EMOJI);
    }
  }

  public void testStandardAnalyzer() throws IOException {
    try (Analyzer analyzer = new StandardAnalyzer()) {
      assertReported(analyzer, "hel.*", "say hello now"); // control
      assertReported(analyzer, ADLAM_ALIF + ".*", "say " + ADLAM_WORD + " now");
    }
  }

  private static Set<String> ngrams(String text) throws IOException {
    Set<String> terms = new HashSet<>();
    try (Analyzer analyzer = new WhitespaceAnalyzer();
        TokenStream ts =
            new RegexpQueryHandler().wrapTermStream(FIELD, analyzer.tokenStream(FIELD, text))) {
      CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(termAtt.toString());
      }
      ts.end();
    }
    return terms;
  }

  /** The presearcher may only skip a query that cannot match the document. */
  private static void assertReported(Analyzer analyzer, String regexp, String text)
      throws IOException {
    String what = "/" + regexp + "/ on \"" + text + "\"";
    assertTrue(what + " matches", reported(analyzer, Presearcher.NO_FILTERING, regexp, text));
    Presearcher withHandler =
        new TermFilteredPresearcher(
            TermWeightor.DEFAULT, List.of(new RegexpQueryHandler()), Set.of());
    assertTrue(
        what + " is not reported with RegexpQueryHandler",
        reported(analyzer, withHandler, regexp, text));
  }

  private static boolean reported(
      Analyzer analyzer, Presearcher presearcher, String regexp, String text) throws IOException {
    try (Monitor monitor = new Monitor(analyzer, presearcher)) {
      monitor.register(new MonitorQuery("q", new RegexpQuery(new Term(FIELD, regexp))));
      Document doc = new Document();
      doc.add(new TextField(FIELD, text, Field.Store.NO));
      return monitor.match(doc, QueryMatch.SIMPLE_MATCHER).matches("q") != null;
    }
  }
}
