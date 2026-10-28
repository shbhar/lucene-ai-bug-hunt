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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ResourceLoader;
import org.apache.lucene.util.ResourceLoaderAware;
import org.apache.lucene.util.Version;

/**
 * Independent check: does ProtectedTermFilterFactory hand its luceneMatchVersion to the filters it
 * builds from wrappedFilters? The observable symptom uses the deprecated truncate prefixLength,
 * whose meaning depends on luceneMatchVersion (UTF-16 chars before 10.5, code points from 10.5).
 *
 * <p>Token "ab" + U+1F600 + U+1F600 + "cd" is 8 UTF-16 chars / 6 code points. prefixLength=4 gives
 * "ab" + U+1F600 (4 chars) under the legacy behaviour and "ab" + U+1F600 + U+1F600 (4 code points)
 * under the new one. The second token is a protected term and must pass through unchanged.
 */
public class TestWrappedFiltersMatchVersion extends LuceneTestCase {

  private static final String OLD = "10.4.0";
  private static final String EMOJI = "😀";
  private static final String INPUT = "ab" + EMOJI + EMOJI + "cd keepthisword";
  private static final String LEGACY_4_CHARS = "ab" + EMOJI; // first 4 UTF-16 chars
  private static final String NEW_4_CODEPOINTS = "ab" + EMOJI + EMOJI; // first 4 code points
  private static final String PROTECTED = "keepthisword";

  /** Serves the same text for every resource name; only the protected-terms file is read. */
  private static final class OneFileLoader implements ResourceLoader {
    @Override
    public InputStream openResource(String resource) {
      return new ByteArrayInputStream((PROTECTED + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
      try {
        return Class.forName(cname).asSubclass(expectedType);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static Map<String, String> map(String... kv) {
    Map<String, String> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(kv[i], kv[i + 1]);
    }
    return m;
  }

  private static List<String> terms(TokenStream ts) throws IOException {
    List<String> out = new ArrayList<>();
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString());
    }
    ts.end();
    ts.close();
    return out;
  }

  private static List<String> run(TokenFilterFactory f) throws IOException {
    if (f instanceof ResourceLoaderAware) {
      ((ResourceLoaderAware) f).inform(new OneFileLoader());
    }
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader(INPUT));
    return terms(f.create(tok));
  }

  private static List<String> run(Analyzer a) throws IOException {
    try (TokenStream ts = a.tokenStream("f", INPUT)) {
      List<String> out = new ArrayList<>();
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
      return out;
    }
  }

  private static boolean truncateIsVersionDependent() throws Exception {
    return Version.LATEST.onOrAfter(Version.parse("10.5.0"));
  }

  // ---- controls -------------------------------------------------------------------------------

  /** Control A: a stand-alone truncate factory pinned to 10.4.0 keeps the legacy char semantics. */
  public void testControlUnwrappedTruncatePinned() throws Exception {
    TokenFilterFactory f =
        TokenFilterFactory.forName(
            "truncate", map("luceneMatchVersion", OLD, "prefixLength", "4"));
    List<String> got = run(f);
    assertEquals(LEGACY_4_CHARS, got.get(0));
  }

  /** Control B: without a match version the same truncate uses code points (10.5+ only). */
  public void testControlUnwrappedTruncateLatest() throws Exception {
    List<String> got = run(TokenFilterFactory.forName("truncate", map("prefixLength", "4")));
    assertEquals(truncateIsVersionDependent() ? NEW_4_CODEPOINTS : LEGACY_4_CHARS, got.get(0));
  }

  /** Control C: the wrapped truncate does honour a version when it is given to it explicitly. */
  public void testControlWrappedWithExplicitInnerVersion() throws Exception {
    TokenFilterFactory f =
        TokenFilterFactory.forName(
            "protectedTerm",
            map(
                "luceneMatchVersion", OLD,
                "protected", "protected.txt",
                "wrappedFilters", "truncate",
                "truncate.prefixLength", "4",
                "truncate.luceneMatchVersion", OLD));
    List<String> got = run(f);
    assertEquals(List.of(LEGACY_4_CHARS, PROTECTED), got);
  }

  /** Control D: CustomAnalyzer when()/endwhen() form with a default match version of 10.4.0. */
  public void testControlCustomAnalyzerWhenForm() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder(new OneFileLoader())
            .withDefaultMatchVersion(Version.parse(OLD))
            .withTokenizer("whitespace")
            .when("protectedTerm", "protected", "protected.txt")
            .addTokenFilter("truncate", "prefixLength", "4")
            .endwhen()
            .build();
    assertEquals(List.of(LEGACY_4_CHARS, PROTECTED), run(a));
  }

  // ---- the claim ------------------------------------------------------------------------------

  /** Claim 1: factory API, luceneMatchVersion=10.4.0 on the outer ProtectedTermFilterFactory. */
  public void testWrappedFiltersFactoryApi() throws Exception {
    TokenFilterFactory f =
        TokenFilterFactory.forName(
            "protectedTerm",
            map(
                "luceneMatchVersion", OLD,
                "protected", "protected.txt",
                "wrappedFilters", "truncate",
                "truncate.prefixLength", "4"));
    assertEquals(Version.parse(OLD), f.getLuceneMatchVersion());
    List<String> got = run(f);
    assertEquals(
        "wrapped truncate should use the outer factory's luceneMatchVersion " + OLD,
        List.of(LEGACY_4_CHARS, PROTECTED),
        got);
  }

  /** Claim 2: CustomAnalyzer.withDefaultMatchVersion(10.4.0) with the wrappedFilters form. */
  public void testWrappedFiltersCustomAnalyzer() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder(new OneFileLoader())
            .withDefaultMatchVersion(Version.parse(OLD))
            .withTokenizer("whitespace")
            .addTokenFilter(
                "protectedTerm",
                "protected",
                "protected.txt",
                "wrappedFilters",
                "truncate",
                "truncate.prefixLength",
                "4")
            .build();
    assertEquals(
        "wrappedFilters form should match the when() form under the same default match version",
        List.of(LEGACY_4_CHARS, PROTECTED),
        run(a));
  }
}
