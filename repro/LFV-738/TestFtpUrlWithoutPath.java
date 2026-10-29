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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * RFC 1738 section 3.2 makes the path of an ftp URL optional (ftpurl = "ftp://" login [ "/" fpath
 * [ ";type=" ftptype ]]), and RFC 3986 allows an empty path-abempty; an ftp URL without a path is
 * not recognized as a URL.
 */
public class TestFtpUrlWithoutPath extends BaseTokenStreamTestCase {

  private static Analyzer tokenizerOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new UAX29URLEmailTokenizer(newAttributeFactory()));
      }
    };
  }

  /** Control: with a trailing slash the URL is recognized. */
  public void testControlWithSlash() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertAnalyzesTo(
          a,
          "get it from ftp://ftp.example.com/ today",
          new String[] {"get", "it", "from", "ftp://ftp.example.com/", "today"},
          new String[] {"<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<URL>", "<ALPHANUM>"});
    }
  }

  public void testFtpWithoutPath() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertAnalyzesTo(
          a,
          "get it from ftp://ftp.example.com today",
          new String[] {"get", "it", "from", "ftp://ftp.example.com", "today"},
          new String[] {"<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<URL>", "<ALPHANUM>"});
    }
  }

  public void testFtpWithoutPathLocalHost() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertAnalyzesTo(a, "ftp://localhost", new String[] {"ftp://localhost"}, new String[] {"<URL>"});
    }
  }

  /** http with no path is recognized; the ftp rule is the odd one out. */
  public void testControlHttpWithoutPath() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertAnalyzesTo(a, "http://localhost", new String[] {"http://localhost"}, new String[] {"<URL>"});
    }
  }
}
