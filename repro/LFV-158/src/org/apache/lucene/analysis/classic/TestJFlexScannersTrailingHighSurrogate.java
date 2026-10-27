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
package org.apache.lucene.analysis.classic;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.email.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/** The other JFlex-generated scanners in analysis-common share the refill code. */
public class TestJFlexScannersTrailingHighSurrogate extends BaseTokenStreamTestCase {

  private static final String TEXT = "hello world\uD83D"; // ends with the first half of U+1F600

  private static List<String> terms(Analyzer a) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", TEXT)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    a.close();
    return out;
  }

  private static Analyzer analyzer(java.util.function.Supplier<Tokenizer> t) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(t.get());
      }
    };
  }

  public void testClassicTokenizer() throws IOException {
    List<String> got = terms(analyzer(ClassicTokenizer::new));
    assertTrue("tokens lost: " + got, got.containsAll(List.of("hello", "world")));
  }

  public void testUAX29URLEmailTokenizer() throws IOException {
    List<String> got = terms(analyzer(UAX29URLEmailTokenizer::new));
    assertTrue("tokens lost: " + got, got.containsAll(List.of("hello", "world")));
  }

  public void testWikipediaTokenizer() throws IOException {
    List<String> got = terms(analyzer(WikipediaTokenizer::new));
    assertTrue("tokens lost: " + got, got.containsAll(List.of("hello", "world")));
  }

  public void testHTMLStripCharFilter() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new WhitespaceTokenizer());
          }

          @Override
          protected Reader initReader(String fieldName, Reader reader) {
            return new HTMLStripCharFilter(reader);
          }
        };
    List<String> got = terms(a);
    assertTrue("text lost by the char filter: " + got, !got.isEmpty() && got.get(0).equals("hello"));
  }
}
