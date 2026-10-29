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
package bomcheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: a synonyms file saved as "UTF-8 with BOM" (bytes EF BB BF at the start) must
 * load the same rules as the same file without the BOM. The file is a real file in a config
 * directory, loaded through the public CustomAnalyzer.builder(Path) API (FilesystemResourceLoader).
 *
 * <p>Each test first checks the no-BOM file against the expected analysis (sanity), then checks the
 * BOM file gives the identical analysis.
 */
public class TestSynonymsFileBomV1 extends LuceneTestCase {

  private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private Path confDir(String fileName, String content, boolean bom) throws IOException {
    Path dir = createTempDir("conf");
    byte[] body = content.getBytes(StandardCharsets.UTF_8);
    byte[] all = new byte[(bom ? BOM.length : 0) + body.length];
    if (bom) {
      System.arraycopy(BOM, 0, all, 0, BOM.length);
    }
    System.arraycopy(body, 0, all, bom ? BOM.length : 0, body.length);
    Files.write(dir.resolve(fileName), all);
    return dir;
  }

  private Analyzer synonymAnalyzer(String filterName, String format, String content, boolean bom)
      throws IOException {
    Path dir = confDir("synonyms.txt", content, bom);
    CustomAnalyzer.Builder b = CustomAnalyzer.builder(dir).withTokenizer("whitespace");
    if (format == null) {
      b.addTokenFilter(filterName, "synonyms", "synonyms.txt");
    } else {
      b.addTokenFilter(filterName, "synonyms", "synonyms.txt", "format", format);
    }
    return b.build();
  }

  /** term@positionIncrement for every token, with U+FEFF made visible. */
  private static List<String> analyze(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString().replace("\uFEFF", "<U+FEFF>") + "@" + posInc.getPositionIncrement());
      }
      ts.end();
    }
    return out;
  }

  private void checkSynonyms(
      String filterName, String format, String content, String text, List<String> expected)
      throws IOException {
    List<String> plain;
    try (Analyzer a = synonymAnalyzer(filterName, format, content, false)) {
      plain = analyze(a, text);
    }
    assertEquals("sanity (file without BOM), text '" + text + "'", expected, plain);
    List<String> withBom;
    try (Analyzer a = synonymAnalyzer(filterName, format, content, true)) {
      withBom = analyze(a, text);
    }
    assertEquals(
        "same file with a UTF-8 BOM must analyze '" + text + "' the same as without it",
        plain,
        withBom);
  }

  // ---- Solr format (default), SynonymGraphFilterFactory ----

  public void testSolrEquivalenceFirstLine() throws IOException {
    checkSynonyms("synonymGraph", null, "foo, bar\nbaz, qux\n", "foo", List.of("bar@1", "foo@0"));
  }

  public void testSolrEquivalenceFirstLineOtherDirection() throws IOException {
    checkSynonyms("synonymGraph", null, "foo, bar\nbaz, qux\n", "bar", List.of("foo@1", "bar@0"));
  }

  /** Control inside the same file: the second line is unaffected by the BOM. */
  public void testSolrSecondLineUnaffected() throws IOException {
    checkSynonyms("synonymGraph", null, "foo, bar\nbaz, qux\n", "baz", List.of("qux@1", "baz@0"));
  }

  public void testSolrExplicitMappingFirstLine() throws IOException {
    checkSynonyms("synonymGraph", null, "baz => qux\n", "baz", List.of("qux@1"));
  }

  public void testSolrCommentFirstLine() throws IOException {
    checkSynonyms("synonymGraph", null, "#couch, sofa\nfoo, bar\n", "sofa", List.of("sofa@1"));
  }

  // ---- WordNet format ----

  public void testWordnetFirstLine() throws IOException {
    String wn =
        "s(100000001,1,'foo',n,1,0).\n"
            + "s(100000001,2,'bar',n,1,0).\n"
            + "s(100000002,1,'baz',n,1,0).\n"
            + "s(100000002,2,'qux',n,1,0).\n";
    checkSynonyms("synonymGraph", "wordnet", wn, "foo", List.of("bar@1", "foo@0"));
  }

  // ---- deprecated SynonymFilterFactory ----

  public void testDeprecatedFactoryFirstLine() throws IOException {
    checkSynonyms("synonym", null, "foo, bar\nbaz, qux\n", "foo", List.of("foo@1", "bar@0"));
  }

  // ---- control: a word-list factory reading the same bytes skips the BOM ----

  public void testControlStopFilterSkipsBom() throws IOException {
    for (boolean bom : new boolean[] {false, true}) {
      Path dir = confDir("stop.txt", "foo\nbar\n", bom);
      try (Analyzer a =
          CustomAnalyzer.builder(dir)
              .withTokenizer("whitespace")
              .addTokenFilter("stop", "words", "stop.txt")
              .build()) {
        assertEquals("bom=" + bom, List.of("baz@3"), analyze(a, "foo bar baz"));
      }
    }
  }
}
