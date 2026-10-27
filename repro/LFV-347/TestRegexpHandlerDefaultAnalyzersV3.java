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
package v3check;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.monitor.Monitor;
import org.apache.lucene.monitor.MonitorQuery;
import org.apache.lucene.monitor.Presearcher;
import org.apache.lucene.monitor.QueryMatch;
import org.apache.lucene.monitor.RegexpQueryHandler;
import org.apache.lucene.monitor.TermFilteredPresearcher;
import org.apache.lucene.monitor.TermWeightor;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Same regexp queries and documents run through Presearcher.NO_FILTERING (ground truth) and
 * TermFilteredPresearcher + RegexpQueryHandler, using stock analyzers (StandardAnalyzer and
 * KeywordAnalyzer) rather than whitespace tokenization. Public API only.
 */
public class TestRegexpHandlerDefaultAnalyzersV3 extends LuceneTestCase {

  private static final String F = "text";

  private static String cp(int... cps) {
    StringBuilder sb = new StringBuilder();
    for (int c : cps) sb.appendCodePoint(c);
    return sb.toString();
  }

  private static boolean matches(Analyzer a, Presearcher p, String regexp, String text)
      throws IOException {
    try (Monitor m = new Monitor(a, p)) {
      m.register(new MonitorQuery("q", new RegexpQuery(new Term(F, regexp))));
      Document d = new Document();
      d.add(new TextField(F, text, Field.Store.NO));
      return m.match(d, QueryMatch.SIMPLE_MATCHER).matches("q") != null;
    }
  }

  private static Presearcher handler() {
    return new TermFilteredPresearcher(
        TermWeightor.DEFAULT, List.of(new RegexpQueryHandler()), Set.of());
  }

  private static void check(Analyzer a, String[][] cases, String label) throws IOException {
    List<String> out = new ArrayList<>();
    List<String> missed = new ArrayList<>();
    for (String[] c : cases) {
      boolean truth = matches(a, Presearcher.NO_FILTERING, c[0], c[1]);
      boolean filtered = matches(a, handler(), c[0], c[1]);
      out.add("/" + c[0] + "/ on \"" + c[1] + "\": truth=" + truth + " filtered=" + filtered);
      if (truth && !filtered) missed.add("/" + c[0] + "/ on \"" + c[1] + "\"");
    }
    System.out.println(label + ": " + out);
    assertTrue(label + " presearcher dropped matching queries: " + missed, missed.isEmpty());
  }

  // Adlam (U+1E900 block, living script) and Osage (U+104B0 block, living script) words are single
  // tokens for StandardTokenizer; the control is a BMP word.
  public void testStandardAnalyzer() throws IOException {
    String adlam = cp(0x1E922, 0x1E926, 0x1E928); // lowercase Adlam letters
    String osage = cp(0x104D8, 0x104DF, 0x104E3); // lowercase Osage letters
    String[][] cases = {
      {"hel.*", "hello world"}, // control
      {adlam.substring(0, 2) + ".*", "x " + adlam + " y"},
      {osage.substring(0, 2) + ".*", "x " + osage + " y"},
    };
    try (Analyzer a = new StandardAnalyzer()) {
      check(a, cases, "StandardAnalyzer");
    }
  }

  // A short keyword value (e.g. a tag or title field) with an emoji.
  public void testKeywordAnalyzer() throws IOException {
    String[][] cases = {
      {"great.*", "great product"}, // control
      {"great.*", "great product " + cp(0x1F600)},
    };
    try (Analyzer a = new KeywordAnalyzer()) {
      check(a, cases, "KeywordAnalyzer");
    }
  }
}
