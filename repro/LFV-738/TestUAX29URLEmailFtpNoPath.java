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
package org.apache.lucene.analysis.email;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * An ftp URL with no path ("ftp://host") should be one {@code <URL>} token, like "http://host".
 *
 * <p>RFC 1738 section 3.2.2: "The whole url-path may be omitted, including the "/" delimiting it".
 */
public class TestUAX29URLEmailFtpNoPath extends BaseTokenStreamTestCase {

  private Analyzer a;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new UAX29URLEmailTokenizer(newAttributeFactory());
            return new TokenStreamComponents(tokenizer);
          }
        };
  }

  @Override
  public void tearDown() throws Exception {
    a.close();
    super.tearDown();
  }

  /** Passes today: an ftp URL with a "/" path, and an http URL with no path. */
  public void testControls() throws Exception {
    assertOneURL(a, "ftp://ftp.example.com/");
    assertOneURL(a, "http://localhost");
  }

  public void testFtpHostInText() throws Exception {
    assertEquals(
        List.of(
            "get <ALPHANUM>",
            "it <ALPHANUM>",
            "from <ALPHANUM>",
            "ftp://ftp.example.com <URL>",
            "today <ALPHANUM>"),
        tokens(a, "get it from ftp://ftp.example.com today"));
  }

  public void testFtpLocalhost() throws Exception {
    assertOneURL(a, "ftp://localhost");
  }

  public void testFtpIPv4() throws Exception {
    assertOneURL(a, "ftp://192.0.2.10");
  }

  public void testFtpUserinfo() throws Exception {
    assertOneURL(a, "ftp://anonymous@ftp.example.org");
  }

  /** The stock analyzer (tokenizer + lower-casing + stop words) behaves the same. */
  public void testAnalyzer() throws Exception {
    try (Analyzer analyzer = new UAX29URLEmailAnalyzer()) {
      assertEquals(
          List.of("ftp://ftp.example.org <URL>"), tokens(analyzer, "FTP://ftp.example.org"));
    }
  }

  private static void assertOneURL(Analyzer analyzer, String url) throws IOException {
    assertEquals(List.of(url + " <URL>"), tokens(analyzer, url));
  }

  /** Returns "term type" for each token. */
  private static List<String> tokens(Analyzer analyzer, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = analyzer.tokenStream("field", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      TypeAttribute type = ts.addAttribute(TypeAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term + " " + type.type());
      }
      ts.end();
    }
    return out;
  }
}
