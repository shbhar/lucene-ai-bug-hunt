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
package org.apache.lucene.analysis.te;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * TeluguAnalyzer runs its StopFilter after its normalization filters, but its default stop word list is
 * not normalized, so default stop words that the normalizers change are never removed.
 */
public class TestTeluguDefaultStopwords extends BaseTokenStreamTestCase {

  private static List<String> terms(TeluguAnalyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(t.toString());
      ts.end();
    }
    return out;
  }

  /** కూడా ("also") is in the default stop set, so analyzing it alone must produce no token. */
  public void testOneDefaultStopword() throws IOException {
    try (TeluguAnalyzer a = new TeluguAnalyzer()) {
      String w = "కూడా";
      assertTrue(a.getStopwordSet().contains(w));
      assertEquals(List.of(), terms(a, w));
    }
  }

  /**
   * Every single-word entry of the default stop set must be removed by the analyzer (entries that
   * contain a space can never match one token and are left out here).
   */
  public void testAllDefaultStopwordsAreRemoved() throws IOException {
    try (TeluguAnalyzer a = new TeluguAnalyzer()) {
      CharArraySet stop = TeluguAnalyzer.getDefaultStopSet();
      List<String> notRemoved = new ArrayList<>();
      for (Object o : stop) {
        String w = new String((char[]) o);
        if (w.indexOf(' ') >= 0) continue;
        List<String> t = terms(a, w);
        if (!t.isEmpty()) notRemoved.add(w + " -> " + t);
      }
      assertEquals(
          "default stop words not removed (" + notRemoved.size() + " of " + stop.size() + "): " + notRemoved,
          0,
          notRemoved.size());
    }
  }
}
