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
package org.apache.lucene.analysis.synonym;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.ResourceLoaderAware;

/**
 * A synonyms file that starts with a UTF-8 byte order mark (BOM, U+FEFF) should load the same
 * rules as the file without it, as the line loader of the other file-based factories
 * (WordlistLoader.getLines, "skip initial bom marker") does. The synonym factories decode the file
 * themselves and keep the BOM glued to the first line.
 *
 * <p>Every test first checks the expectation on the same file without the BOM (passes), then on
 * the BOM-prefixed file (fails on main).
 */
public class TestSynonymFileUtf8Bom extends BaseTokenStreamTestCase {

  private static final String BOM = "\uFEFF";

  private static Analyzer analyzer(String spi, String fileContent, String... extraArgs)
      throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("synonyms", "synonyms.txt");
    for (int i = 0; i < extraArgs.length; i += 2) {
      args.put(extraArgs[i], extraArgs[i + 1]);
    }
    TokenFilterFactory factory = TokenFilterFactory.forName(spi, args);
    ((ResourceLoaderAware) factory).inform(new StringMockResourceLoader(fileContent));
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        return new TokenStreamComponents(tokenizer, factory.create(tokenizer));
      }
    };
  }

  /** Solr format (the default), first rule is an equivalence: 'foo' loses its synonym. */
  public void testSolrFormatFirstRule() throws Exception {
    String rules = "foo, bar\nbaz, qux\n";
    try (Analyzer a = analyzer("synonymGraph", rules)) {
      assertAnalyzesTo(a, "foo", new String[] {"bar", "foo"}, new int[] {1, 0});
    }
    try (Analyzer a = analyzer("synonymGraph", BOM + rules)) {
      // main: [foo] only, and 'bar' expands to the invisible term "\uFEFFfoo"
      assertAnalyzesTo(a, "foo", new String[] {"bar", "foo"}, new int[] {1, 0});
    }
  }

  /** Solr format, first rule is an explicit mapping: it never fires. */
  public void testSolrFormatFirstExplicitMapping() throws Exception {
    String rules = "baz => qux\n";
    try (Analyzer a = analyzer("synonymGraph", rules)) {
      assertAnalyzesTo(a, "baz", new String[] {"qux"});
    }
    try (Analyzer a = analyzer("synonymGraph", BOM + rules)) {
      // main: [baz]
      assertAnalyzesTo(a, "baz", new String[] {"qux"});
    }
  }

  /**
   * Solr format, the first line is a commented-out rule: with the BOM it is no longer a comment
   * ("lines starting with '#' are comments"), so the disabled rule becomes active.
   */
  public void testSolrFormatCommentedOutFirstLine() throws Exception {
    String rules = "#couch, sofa\nfoo, bar\n";
    try (Analyzer a = analyzer("synonymGraph", rules)) {
      assertAnalyzesTo(a, "sofa", new String[] {"sofa"});
    }
    try (Analyzer a = analyzer("synonymGraph", BOM + rules)) {
      // main: [\uFEFF#couch, sofa]
      assertAnalyzesTo(a, "sofa", new String[] {"sofa"});
    }
  }

  /** WordNet format: the first line's synset id is read shifted, so its word leaves the synset. */
  public void testWordnetFormatFirstLine() throws Exception {
    String rules = "s(100000001,1,'foo',n,1,0).\ns(100000001,2,'bar',n,1,0).\n";
    try (Analyzer a = analyzer("synonymGraph", rules, "format", "wordnet")) {
      assertAnalyzesTo(a, "foo", new String[] {"bar", "foo"}, new int[] {1, 0});
    }
    try (Analyzer a = analyzer("synonymGraph", BOM + rules, "format", "wordnet")) {
      // main: [foo]
      assertAnalyzesTo(a, "foo", new String[] {"bar", "foo"}, new int[] {1, 0});
    }
  }

  /** The deprecated SynonymFilterFactory reads the file the same way. */
  @SuppressWarnings("deprecation")
  public void testDeprecatedSynonymFilterFactory() throws Exception {
    String rules = "foo, bar\n";
    try (Analyzer a = analyzer("synonym", rules)) {
      assertAnalyzesTo(a, "foo", new String[] {"foo", "bar"}, new int[] {1, 0});
    }
    try (Analyzer a = analyzer("synonym", BOM + rules)) {
      // main: [foo]
      assertAnalyzesTo(a, "foo", new String[] {"foo", "bar"}, new int[] {1, 0});
    }
  }

  /** Control: a words file read through WordlistLoader.getLines skips the BOM (passes on main). */
  public void testControlStopFilterFactorySkipsBom() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("words", "stop.txt");
    TokenFilterFactory factory = TokenFilterFactory.forName("stop", args);
    ((ResourceLoaderAware) factory).inform(new StringMockResourceLoader(BOM + "foo\nbar\n"));
    try (Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new WhitespaceTokenizer();
            return new TokenStreamComponents(tokenizer, factory.create(tokenizer));
          }
        }) {
      assertAnalyzesTo(a, "foo baz", new String[] {"baz"}, new int[] {2});
    }
  }
}
