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

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.Version;

/**
 * ProtectedTermFilterFactory documents two equivalent ways to configure the filters it wraps: the
 * CustomAnalyzer when(...)/endwhen() form and the wrappedFilters / filterName.argName form (both
 * examples use truncate with prefixLength). TruncateTokenFilterFactory's prefixLength behaviour
 * depends on luceneMatchVersion (legacy UTF-16 char truncation before 10.5). The wrappedFilters
 * form builds the wrapped factories without the luceneMatchVersion that ProtectedTermFilterFactory
 * itself was given, so the wrapped truncate silently switches to the 10.5+ code point behaviour.
 */
public class TestProtectedTermWrappedMatchVersion extends BaseTokenStreamTestCase {

  // "ab" + two emoji (each a surrogate pair) + "cd": 8 UTF-16 chars, 6 code points
  private static final String TOKEN = "ab😀😀cd";
  private static final String TEXT = TOKEN + " keep";

  /** Reference: truncate outside the wrapper with the same match version (legacy char mode). */
  public void testUnwrappedTruncateHonoursMatchVersion() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder(new StringMockResourceLoader("keep"))
            .withDefaultMatchVersion(Version.LUCENE_10_4_0)
            .withTokenizer("whitespace")
            .addTokenFilter("truncate", "prefixLength", "4")
            .build();
    // legacy behaviour: the first 4 UTF-16 chars
    assertAnalyzesTo(a, TEXT, new String[] {"ab😀", "keep"});
    a.close();
  }

  /** The when()/endwhen() form passes the match version on to the wrapped filter. */
  public void testWhenFormHonoursMatchVersion() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder(new StringMockResourceLoader("keep"))
            .withDefaultMatchVersion(Version.LUCENE_10_4_0)
            .withTokenizer("whitespace")
            .when("protectedTerm", "protected", "protectedTerms.txt")
            .addTokenFilter("truncate", "prefixLength", "4")
            .endwhen()
            .build();
    assertAnalyzesTo(a, TEXT, new String[] {"ab😀", "keep"});
    a.close();
  }

  /** The documented wrappedFilters form must behave like the when()/endwhen() form. */
  public void testWrappedFiltersFormHonoursMatchVersion() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder(new StringMockResourceLoader("keep"))
            .withDefaultMatchVersion(Version.LUCENE_10_4_0)
            .withTokenizer("whitespace")
            .addTokenFilter(
                "protectedTerm",
                "protected",
                "protectedTerms.txt",
                "wrappedFilters",
                "truncate",
                "truncate.prefixLength",
                "4")
            .build();
    assertAnalyzesTo(a, TEXT, new String[] {"ab😀", "keep"});
    a.close();
  }

  /** Same through the factory API with an explicit luceneMatchVersion argument. */
  public void testWrappedFiltersFactoryApi() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("luceneMatchVersion", "10.4.0");
    args.put("protected", "protectedTerms.txt");
    args.put("wrappedFilters", "truncate");
    args.put("truncate.prefixLength", "4");
    ProtectedTermFilterFactory f = new ProtectedTermFilterFactory(args);
    f.inform(new StringMockResourceLoader("keep"));
    assertEquals(Version.LUCENE_10_4_0, f.getLuceneMatchVersion());
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            org.apache.lucene.analysis.Tokenizer t =
                new org.apache.lucene.analysis.core.WhitespaceTokenizer();
            return new TokenStreamComponents(t, f.create(t));
          }
        };
    assertAnalyzesTo(a, TEXT, new String[] {"ab😀", "keep"});
    a.close();
  }
}
