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
package org.apache.lucene.analysis.core;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

/**
 * A words file saved as UTF-8 with a byte order mark (as some editors do) loads correctly in the
 * default "wordset" format (the BOM is skipped, SOLR-1865), but in the "snowball" format the BOM
 * stays glued to the first word, so that word silently stops being a stop word / keep word.
 *
 * <p>Public API only: StopFilterFactory, KeepWordFilterFactory (both via CustomAnalyzer) and
 * StopFilterFactory#getStopWords.
 */
public class TestSnowballFormatUtf8Bom extends BaseTokenStreamTestCase {

  private static final String BOM = "\uFEFF";

  private static Analyzer stop(String format, String file) throws Exception {
    return CustomAnalyzer.builder(new StringMockResourceLoader(file))
        .withTokenizer("whitespace")
        .addTokenFilter("stop", "words", "stopwords.txt", "format", format)
        .build();
  }

  /** Control: the same words, one per line, in the default wordset format. */
  public void testWordsetFormatSkipsBom() throws Exception {
    try (Analyzer a = stop("wordset", BOM + "the\na\nan\n")) {
      assertAnalyzesTo(a, "the cat", new String[] {"cat"});
    }
  }

  /** The snowball format: the first word of a BOM-prefixed file is not a stop word. */
  public void testSnowballFormatSkipsBom() throws Exception {
    try (Analyzer a = stop("snowball", BOM + "the a an | articles\n")) {
      assertAnalyzesTo(a, "the cat", new String[] {"cat"});
    }
  }

  /** Same root seen through the factory's parsed set. */
  public void testSnowballStopSetContainsFirstWord() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("words", "stopwords.txt");
    args.put("format", "snowball");
    StopFilterFactory f = new StopFilterFactory(args);
    f.inform(new StringMockResourceLoader(BOM + "the a an | articles\n"));
    assertTrue(
        "stop set " + f.getStopWords() + " must contain the first word 'the'",
        f.getStopWords().contains("the"));
  }

  /** KeepWordFilterFactory shares the loader: the first keep word's tokens are dropped. */
  public void testSnowballKeepWordsKeepFirstWord() throws Exception {
    try (Analyzer a =
        CustomAnalyzer.builder(new StringMockResourceLoader(BOM + "apple banana\n"))
            .withTokenizer("whitespace")
            .addTokenFilter("keepWord", "words", "keep.txt", "format", "snowball")
            .build()) {
      assertAnalyzesTo(a, "apple cherry banana", new String[] {"apple", "banana"});
    }
  }
}
