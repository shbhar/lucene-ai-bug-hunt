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
package verify.stopsets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.bg.BulgarianAnalyzer;
import org.apache.lucene.analysis.ckb.SoraniAnalyzer;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.lv.LatvianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * Independent check (public API only, package outside org.apache.lucene) of the claim that
 * SoraniAnalyzer, LatvianAnalyzer and GalicianAnalyzer put the '#' comment lines of their bundled
 * stop word files into getDefaultStopSet().
 *
 * <p>Oracle 1: a whole line that begins with '#' in these files is a comment, not a stop word. The
 * oracle for "comment" is the documented "wordset" stop word file format of StopFilterFactory
 * ("allows whole line comments beginning with the '#' character").
 *
 * <p>Oracle 2: loading the very same bundled file through Lucene's own StopFilterFactory (default
 * "wordset" format) must give the same set as getDefaultStopSet().
 *
 * <p>Tests named testControl* are expected to pass on an affected build; they show the checks are
 * not vacuous and bound the impact (stock analyzer output is not claimed to change).
 */
public class TestBundledStopSetCommentsV1 extends LuceneTestCase {

  // ---------------------------------------------------------------- helpers

  private static List<String> rawLines(Class<?> owner, String resource) throws IOException {
    List<String> lines = new ArrayList<>();
    try (InputStream in = owner.getResourceAsStream(resource)) {
      assertNotNull("bundled resource missing: " + owner.getName() + "/" + resource, in);
      BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      for (String line; (line = br.readLine()) != null; ) {
        lines.add(line);
      }
    }
    return lines;
  }

  /** distinct trimmed whole-line comments of the bundled file */
  private static TreeSet<String> commentLines(Class<?> owner, String resource) throws IOException {
    TreeSet<String> out = new TreeSet<>();
    for (String line : rawLines(owner, resource)) {
      if (line.startsWith("#")) {
        out.add(line.trim());
      }
    }
    return out;
  }

  private static TreeSet<String> asStrings(CharArraySet set) {
    TreeSet<String> out = new TreeSet<>();
    for (Object o : set) {
      out.add(new String((char[]) o));
    }
    return out;
  }

  private static TreeSet<String> entriesStartingWithHash(CharArraySet set) {
    TreeSet<String> out = new TreeSet<>();
    for (String s : asStrings(set)) {
      if (s.startsWith("#")) {
        out.add(s);
      }
    }
    return out;
  }

  /** the same bundled file, loaded through StopFilterFactory's documented default format */
  private static CharArraySet factoryLoaded(Class<?> owner, String resource) throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("words", resource);
    StopFilterFactory f = new StopFilterFactory(args);
    f.inform(new ClasspathResourceLoader(owner));
    return f.getStopWords();
  }

  private static List<String> tokens(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(t.toString());
      }
      ts.end();
    }
    return out;
  }

  private static List<String> whitespaceStop(CharArraySet stop, String text) throws IOException {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader(text));
    List<String> out = new ArrayList<>();
    try (TokenStream ts = new StopFilter(tok, stop)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(t.toString());
      }
      ts.end();
    }
    return out;
  }

  private static void assertNoCommentEntries(String name, Class<?> owner, CharArraySet defaults)
      throws IOException {
    TreeSet<String> comments = commentLines(owner, "stopwords.txt");
    TreeSet<String> bad = entriesStartingWithHash(defaults);
    TreeSet<String> badThatAreFileComments = new TreeSet<>(bad);
    badThatAreFileComments.retainAll(comments);
    String msg =
        name
            + ".getDefaultStopSet() has "
            + bad.size()
            + " of "
            + defaults.size()
            + " entries starting with '#' ("
            + badThatAreFileComments.size()
            + " of them verbatim comment lines of the bundled file, which has "
            + comments.size()
            + " distinct comment lines); first ones: "
            + new ArrayList<>(bad).subList(0, Math.min(5, bad.size()));
    assertEquals(msg, 0, bad.size());
  }

  private static void assertEqualsFactory(String name, Class<?> owner, CharArraySet defaults)
      throws IOException {
    TreeSet<String> viaDefault = asStrings(defaults);
    TreeSet<String> viaFactory = asStrings(factoryLoaded(owner, "stopwords.txt"));
    TreeSet<String> onlyDefault = new TreeSet<>(viaDefault);
    onlyDefault.removeAll(viaFactory);
    TreeSet<String> onlyFactory = new TreeSet<>(viaFactory);
    onlyFactory.removeAll(viaDefault);
    assertTrue(
        name
            + ": getDefaultStopSet() size "
            + viaDefault.size()
            + " vs StopFilterFactory(words=same bundled file) size "
            + viaFactory.size()
            + "; only in default set: "
            + onlyDefault.size()
            + " "
            + new ArrayList<>(onlyDefault).subList(0, Math.min(5, onlyDefault.size()))
            + "; only in factory set: "
            + onlyFactory,
        onlyDefault.isEmpty() && onlyFactory.isEmpty());
  }

  // ------------------------------------------------------ claimed defects

  public void testSoraniDefaultSetHasNoCommentEntries() throws IOException {
    assertNoCommentEntries("SoraniAnalyzer", SoraniAnalyzer.class, SoraniAnalyzer.getDefaultStopSet());
  }

  public void testLatvianDefaultSetHasNoCommentEntries() throws IOException {
    assertNoCommentEntries(
        "LatvianAnalyzer", LatvianAnalyzer.class, LatvianAnalyzer.getDefaultStopSet());
  }

  public void testGalicianDefaultSetHasNoCommentEntries() throws IOException {
    assertNoCommentEntries(
        "GalicianAnalyzer", GalicianAnalyzer.class, GalicianAnalyzer.getDefaultStopSet());
  }

  public void testSoraniDefaultSetEqualsFactoryLoadedFile() throws IOException {
    assertEqualsFactory("SoraniAnalyzer", SoraniAnalyzer.class, SoraniAnalyzer.getDefaultStopSet());
  }

  public void testLatvianDefaultSetEqualsFactoryLoadedFile() throws IOException {
    assertEqualsFactory(
        "LatvianAnalyzer", LatvianAnalyzer.class, LatvianAnalyzer.getDefaultStopSet());
  }

  public void testGalicianDefaultSetEqualsFactoryLoadedFile() throws IOException {
    assertEqualsFactory(
        "GalicianAnalyzer", GalicianAnalyzer.class, GalicianAnalyzer.getDefaultStopSet());
  }

  /** Reusing the public default set with another tokenizer: a bare '#' is not a Latvian word. */
  public void testLatvianDefaultSetKeepsHashTokenUnderWhitespaceTokenizer() throws IOException {
    assertEquals(
        "WhitespaceTokenizer + StopFilter(LatvianAnalyzer.getDefaultStopSet())",
        List.of("lucene", "#", "search"),
        whitespaceStop(LatvianAnalyzer.getDefaultStopSet(), "lucene # search"));
  }

  // ------------------------------------------------------------- controls

  /** the files really contain '#' lines, so the passing direction of the oracle is not vacuous */
  public void testControlBundledFilesContainCommentLines() throws IOException {
    assertTrue(commentLines(SoraniAnalyzer.class, "stopwords.txt").size() > 0);
    assertTrue(commentLines(LatvianAnalyzer.class, "stopwords.txt").size() > 0);
    assertTrue(commentLines(GalicianAnalyzer.class, "stopwords.txt").size() > 0);
    assertTrue(commentLines(BulgarianAnalyzer.class, "stopwords.txt").size() > 0);
    assertTrue(commentLines(ArabicAnalyzer.class, "stopwords.txt").size() > 0);
    assertTrue(commentLines(HindiAnalyzer.class, "stopwords.txt").size() > 0);
  }

  /** sibling analyzers whose files also carry '#' comments, loaded with the comment marker */
  public void testControlSiblingsHaveNoCommentEntriesAndMatchFactory() throws IOException {
    assertNoCommentEntries(
        "BulgarianAnalyzer", BulgarianAnalyzer.class, BulgarianAnalyzer.getDefaultStopSet());
    assertNoCommentEntries("ArabicAnalyzer", ArabicAnalyzer.class, ArabicAnalyzer.getDefaultStopSet());
    assertNoCommentEntries("HindiAnalyzer", HindiAnalyzer.class, HindiAnalyzer.getDefaultStopSet());
    assertEqualsFactory(
        "BulgarianAnalyzer", BulgarianAnalyzer.class, BulgarianAnalyzer.getDefaultStopSet());
    assertEqualsFactory("ArabicAnalyzer", ArabicAnalyzer.class, ArabicAnalyzer.getDefaultStopSet());
  }

  /** every real (non-comment) word of each file is in the default set: nothing is lost */
  public void testControlNoRealStopWordMissing() throws IOException {
    Object[][] cases = {
      {SoraniAnalyzer.class, SoraniAnalyzer.getDefaultStopSet()},
      {LatvianAnalyzer.class, LatvianAnalyzer.getDefaultStopSet()},
      {GalicianAnalyzer.class, GalicianAnalyzer.getDefaultStopSet()},
    };
    for (Object[] c : cases) {
      Class<?> owner = (Class<?>) c[0];
      CharArraySet defaults = (CharArraySet) c[1];
      for (String w : asStrings(factoryLoaded(owner, "stopwords.txt"))) {
        assertTrue(owner.getSimpleName() + " misses real stop word " + w, defaults.contains(w));
      }
    }
  }

  /**
   * Bounds the impact: the stock analyzers give the same tokens with the default set and with the
   * comment-free set, on every line of the bundled file and a few extra inputs.
   */
  public void testControlStockAnalyzerOutputUnchanged() throws IOException {
    Object[][] cases = {
      {SoraniAnalyzer.class, new SoraniAnalyzer(), "s"},
      {LatvianAnalyzer.class, new LatvianAnalyzer(), "l"},
      {GalicianAnalyzer.class, new GalicianAnalyzer(), "g"},
    };
    for (Object[] c : cases) {
      Class<?> owner = (Class<?>) c[0];
      Analyzer stock = (Analyzer) c[1];
      CharArraySet clean = factoryLoaded(owner, "stopwords.txt");
      Analyzer fixed =
          owner == SoraniAnalyzer.class
              ? new SoraniAnalyzer(clean)
              : owner == LatvianAnalyzer.class
                  ? new LatvianAnalyzer(clean)
                  : new GalicianAnalyzer(clean);
      List<String> inputs = new ArrayList<>(rawLines(owner, "stopwords.txt"));
      inputs.add("lucene # search");
      inputs.add("#");
      inputs.add("# and");
      for (String in : inputs) {
        assertEquals(
            owner.getSimpleName() + " tokens for <" + in + ">", tokens(fixed, in), tokens(stock, in));
      }
      stock.close();
      fixed.close();
    }
  }
}
