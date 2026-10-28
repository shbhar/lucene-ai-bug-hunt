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
package org.apache.lucene.analysis.miscellaneous;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.Version;

/**
 * Characterisation probe (prints what each configuration produces; asserts nothing about the
 * bug). Rows: the class javadoc's Solr example (wrappedFilters="truncate,lowercase",
 * truncate.prefixLength=4, ignoreCase=true) and truncate with no length parameter (default 5),
 * each unwrapped, in the when() form and in the wrappedFilters form, all pinned to 10.4.0; plus
 * the workaround truncateAfterChars=4 and the unpinned default.
 */
public class TestLfv510Probe extends BaseTokenStreamTestCase {

  private static String show(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      if (cp < 0x80) {
        sb.append((char) cp);
      } else {
        sb.append(String.format(java.util.Locale.ROOT, "<U+%04X>", cp));
      }
      i += Character.charCount(cp);
    }
    return sb.toString() + " (" + s.length() + " chars)";
  }

  private static List<String> terms(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(show(t.toString()));
      }
      ts.end();
    }
    return out;
  }

  private static CustomAnalyzer.Builder b(Version v) throws Exception {
    CustomAnalyzer.Builder b = CustomAnalyzer.builder(new StringMockResourceLoader("keepme"));
    if (v != null) {
      b.withDefaultMatchVersion(v);
    }
    return b.withTokenizer("whitespace");
  }

  private static void row(String label, Analyzer a, String text) throws Exception {
    System.out.println("PROBE " + label + " | " + text + " -> " + terms(a, text));
    a.close();
  }

  public void testProbe() throws Exception {
    Version v = Version.LUCENE_10_4_0;
    String text = "AB😀😀CD KeepMe";
    // the class javadoc's Solr example: truncate,lowercase with truncate.prefixLength=4, ignoreCase
    row(
        "javadoc-solr-example unwrapped(truncate4,lowercase) 10.4.0",
        b(v).addTokenFilter("truncate", "prefixLength", "4").addTokenFilter("lowercase").build(),
        text);
    row(
        "javadoc-solr-example when() 10.4.0",
        b(v).when("protectedTerm", "ignoreCase", "true", "protected", "p.txt")
            .addTokenFilter("truncate", "prefixLength", "4")
            .addTokenFilter("lowercase")
            .endwhen()
            .build(),
        text);
    row(
        "javadoc-solr-example wrappedFilters 10.4.0",
        b(v).addTokenFilter(
                "protectedTerm",
                "ignoreCase",
                "true",
                "protected",
                "p.txt",
                "wrappedFilters",
                "truncate,lowercase",
                "truncate.prefixLength",
                "4")
            .build(),
        text);
    // truncate without a length parameter: default 5, prefixLength path
    String t2 = "ab😀😀cd keepme";
    row("no-length unwrapped 10.4.0", b(v).addTokenFilter("truncate").build(), t2);
    row(
        "no-length when() 10.4.0",
        b(v).when("protectedTerm", "protected", "p.txt").addTokenFilter("truncate").endwhen().build(),
        t2);
    row(
        "no-length wrappedFilters 10.4.0",
        b(v).addTokenFilter("protectedTerm", "protected", "p.txt", "wrappedFilters", "truncate")
            .build(),
        t2);
    // workarounds and the unpinned default
    row(
        "workaround truncate.luceneMatchVersion=10.4.0 wrappedFilters 10.4.0",
        b(v).addTokenFilter(
                "protectedTerm",
                "protected",
                "p.txt",
                "wrappedFilters",
                "truncate",
                "truncate.prefixLength",
                "4",
                "truncate.luceneMatchVersion",
                "10.4.0")
            .build(),
        t2);
    row(
        "workaround truncate.truncateAfterChars=4 wrappedFilters 10.4.0",
        b(v).addTokenFilter(
                "protectedTerm",
                "protected",
                "p.txt",
                "wrappedFilters",
                "truncate",
                "truncate.truncateAfterChars",
                "4")
            .build(),
        t2);
    row(
        "unpinned wrappedFilters prefixLength=4 (LATEST)",
        b(null)
            .addTokenFilter(
                "protectedTerm",
                "protected",
                "p.txt",
                "wrappedFilters",
                "truncate",
                "truncate.prefixLength",
                "4")
            .build(),
        t2);
    // BMP-only text is unaffected
    row(
        "bmp-only wrappedFilters prefixLength=4 10.4.0",
        b(v).addTokenFilter(
                "protectedTerm",
                "protected",
                "p.txt",
                "wrappedFilters",
                "truncate",
                "truncate.prefixLength",
                "4")
            .build(),
        "abécdef keepme");
  }
}
