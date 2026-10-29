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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * UAX29URLEmailTokenizer: an ftp URL whose path is omitted ("ftp://host", "ftp://host:port",
 * "ftp://user@host") should be one URL token. RFC 1738 section 3.2 gives the ftp grammar as
 * {@code ftpurl = "ftp://" login [ "/" fpath [ ";type=" ftptype ]]}, i.e. the path is optional,
 * and the tokenizer's class javadoc says URLs are tokenized according to the relevant RFCs.
 *
 * <p>Uses only public API (constructor, setReader, TokenStream attributes).
 */
public class TestFtpUrlNoPath extends BaseTokenStreamTestCase {

  /** Tokenizes {@code text} and returns "term/type" pairs. */
  private static List<String> tokens(String text) throws IOException {
    List<String> out = new ArrayList<>();
    UAX29URLEmailTokenizer tok = new UAX29URLEmailTokenizer();
    tok.setReader(new StringReader(text));
    CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
    TypeAttribute type = tok.addAttribute(TypeAttribute.class);
    tok.reset();
    while (tok.incrementToken()) {
      out.add(term.toString() + "/" + type.type());
    }
    tok.end();
    tok.close();
    return out;
  }

  private static void assertTokens(String text, String... expected) throws IOException {
    assertEquals("tokens of '" + text + "'", List.of(expected), tokens(text));
  }

  // ---- bug cases: no path after the authority ----

  public void testFtpHostNoPathAlone() throws IOException {
    assertTokens("ftp://ftp.example.org", "ftp://ftp.example.org/<URL>");
  }

  public void testFtpHostNoPathInText() throws IOException {
    assertTokens(
        "mirror at ftp://files.example.net today",
        "mirror/<ALPHANUM>",
        "at/<ALPHANUM>",
        "ftp://files.example.net/<URL>",
        "today/<ALPHANUM>");
  }

  public void testFtpHostPortNoPath() throws IOException {
    assertTokens("ftp://mirror.example.org:2121", "ftp://mirror.example.org:2121/<URL>");
  }

  public void testFtpUserHostNoPath() throws IOException {
    assertTokens("ftp://anonymous@ftp.example.org", "ftp://anonymous@ftp.example.org/<URL>");
  }

  public void testFtpIPv4NoPath() throws IOException {
    assertTokens("ftp://192.0.2.10", "ftp://192.0.2.10/<URL>");
  }

  /** Same through the analyzer (which lower-cases and drops stop words). */
  public void testFtpNoPathViaAnalyzer() throws IOException {
    Analyzer a = new UAX29URLEmailAnalyzer();
    try {
      assertAnalyzesTo(
          a,
          "FTP://Ftp.Example.Org",
          new String[] {"ftp://ftp.example.org"},
          new String[] {"<URL>"});
    } finally {
      a.close();
    }
  }

  // ---- controls: these pass on the current grammar ----

  public void testControlFtpWithSlash() throws IOException {
    assertTokens("ftp://ftp.example.org/", "ftp://ftp.example.org//<URL>");
  }

  public void testControlFtpWithPath() throws IOException {
    assertTokens("ftp://ftp.example.org/pub/x.txt", "ftp://ftp.example.org/pub/x.txt/<URL>");
  }

  public void testControlHttpNoPath() throws IOException {
    assertTokens("http://www.example.org", "http://www.example.org/<URL>");
  }

  public void testControlHttpPortNoPath() throws IOException {
    assertTokens("http://mirror.example.org:2121", "http://mirror.example.org:2121/<URL>");
  }

  /** Not a claim of the bug: just pins the stream of a TokenStream-level consumer. */
  public void testControlAnalyzerHttpNoPath() throws IOException {
    Analyzer a = new UAX29URLEmailAnalyzer();
    try (TokenStream ts = a.tokenStream("f", "HTTP://Www.Example.Org")) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      assertEquals("http://www.example.org", term.toString());
      assertFalse(ts.incrementToken());
      ts.end();
    } finally {
      a.close();
    }
  }
}
