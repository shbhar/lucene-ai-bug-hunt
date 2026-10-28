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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamFactoryTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.Version;

/**
 * The filters that ProtectedTermFilterFactory builds from {@code wrappedFilters} should use its
 * luceneMatchVersion, as the same filter does unwrapped or in the CustomAnalyzer when()/endwhen()
 * form. TruncateTokenFilterFactory's prefixLength depends on that version: the first N UTF-16 chars
 * before 10.5, the first N code points from 10.5 on.
 */
public class TestProtectedTermFilterFactoryMatchVersion extends BaseTokenStreamFactoryTestCase {

  // "ab", two emoji (1 code point and 2 UTF-16 chars each), "cd": 8 chars, 6 code points.
  // "keepme" is protected, so it is never truncated.
  private static final String TEXT = "ab😀😀cd keepme";
  private static final String LEGACY = "ab😀"; // first 4 UTF-16 chars (version < 10.5)
  private static final String CODE_POINTS = "ab😀😀"; // first 4 code points (version >= 10.5)

  private static CustomAnalyzer.Builder pinnedTo10_4() throws IOException {
    return CustomAnalyzer.builder(new StringMockResourceLoader("keepme"))
        .withDefaultMatchVersion(Version.LUCENE_10_4_0)
        .withTokenizer("whitespace");
  }

  /** Control: truncate on its own (nothing protected) uses the pinned version. */
  public void testUnwrappedTruncate() throws Exception {
    Analyzer a = pinnedTo10_4().addTokenFilter("truncate", "prefixLength", "4").build();
    assertAnalyzesTo(a, TEXT, new String[] {LEGACY, "keep"});
    a.close();
  }

  /** Control: the when()/endwhen() form passes the version on to the wrapped truncate. */
  public void testWhenForm() throws Exception {
    Analyzer a =
        pinnedTo10_4()
            .when("protectedTerm", "protected", "protected.txt")
            .addTokenFilter("truncate", "prefixLength", "4")
            .endwhen()
            .build();
    assertAnalyzesTo(a, TEXT, new String[] {LEGACY, "keepme"});
    a.close();
  }

  /** The same chain in the wrappedFilters form. */
  public void testWrappedFiltersForm() throws Exception {
    Analyzer a =
        pinnedTo10_4()
            .addTokenFilter(
                "protectedTerm",
                "protected",
                "protected.txt",
                "wrappedFilters",
                "truncate",
                "truncate.prefixLength",
                "4")
            .build();
    assertAnalyzesTo(a, TEXT, new String[] {LEGACY, "keepme"});
    a.close();
  }

  /** The wrappedFilters form through the factory API, with luceneMatchVersion=10.4.0. */
  public void testWrappedFiltersFactoryArgs() throws Exception {
    ProtectedTermFilterFactory factory =
        (ProtectedTermFilterFactory)
            tokenFilterFactory(
                "protectedTerm",
                Version.LUCENE_10_4_0,
                new StringMockResourceLoader("keepme"),
                "protected",
                "protected.txt",
                "wrappedFilters",
                "truncate",
                "truncate.prefixLength",
                "4");
    assertEquals(Version.LUCENE_10_4_0, factory.getLuceneMatchVersion());
    TokenStream stream = factory.create(whitespaceMockTokenizer(TEXT));
    assertTokenStreamContents(stream, new String[] {LEGACY, "keepme"});
  }

  /** A luceneMatchVersion given to the wrapped filter itself still wins. */
  public void testWrappedFilterOwnVersionWins() throws Exception {
    Analyzer a =
        pinnedTo10_4()
            .addTokenFilter(
                "protectedTerm",
                "protected",
                "protected.txt",
                "wrappedFilters",
                "truncate",
                "truncate.prefixLength",
                "4",
                "truncate.luceneMatchVersion",
                "10.5.0")
            .build();
    assertAnalyzesTo(a, TEXT, new String[] {CODE_POINTS, "keepme"});
    a.close();
  }
}
