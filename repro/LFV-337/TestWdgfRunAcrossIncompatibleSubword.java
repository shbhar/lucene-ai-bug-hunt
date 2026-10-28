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
package org.apache.lucene.analysis.wdgfv1;

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.CATENATE_NUMBERS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.CATENATE_WORDS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterIterator;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.QueryBuilder;

/**
 * Independent verifier repro (public API only; the test lives outside the filter's package).
 *
 * <p>Claim under test: when the first subword of a catenation run has a word type that has both
 * the ALPHA and the DIGIT bits set (every UTF-16 surrogate, or a character mapped to ALPHANUM in
 * the char type table), a later subword that is not appended to the run also does not end the
 * run. The catenated token then (a) gets a position length that spans the skipped subword, and
 * (b) with CATENATE_NUMBERS, joins number parts that are not adjacent.
 *
 * <p>Oracle (class javadoc of WordDelimiterGraphFilter): CATENATE_WORDS catenates "maximum runs of
 * non-numeric subwords" "produced at the same position of the last subword in the run";
 * CATENATE_NUMBERS "works like CATENATE_WORDS, but for adjacent digit sequences"; the filter
 * produces "a correct token graph so that e.g. PhraseQuery can work correctly when this filter is
 * used in the search-time analyzer".
 */
public class TestWdgfRunAcrossIncompatibleSubword extends BaseTokenStreamTestCase {

  /** U+20000, a CJK ideograph (Character.getType = OTHER_LETTER): a letter outside the BMP. */
  private static final String CJK_B = new String(Character.toChars(0x20000));

  /** U+1F525, an emoji outside the BMP. */
  private static final String EMOJI = new String(Character.toChars(0x1F525));

  private static final int WORDS = GENERATE_WORD_PARTS | GENERATE_NUMBER_PARTS | CATENATE_WORDS;
  private static final int NUMBERS = GENERATE_WORD_PARTS | GENERATE_NUMBER_PARTS | CATENATE_NUMBERS;

  private static Tokenizer whitespace(String text) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(text));
    return t;
  }

  /** Renders every token as term[startPos,endPos), in emission order. */
  private static List<String> graph(TokenStream ts) throws IOException {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute inc = ts.addAttribute(PositionIncrementAttribute.class);
    PositionLengthAttribute len = ts.addAttribute(PositionLengthAttribute.class);
    List<String> out = new ArrayList<>();
    int pos = -1;
    ts.reset();
    while (ts.incrementToken()) {
      pos += inc.getPositionIncrement();
      out.add(term + "[" + pos + "," + (pos + len.getPositionLength()) + ")");
    }
    ts.end();
    ts.close();
    return out;
  }

  /**
   * Graph-consistency oracle: every token whose span covers more than one position must equal the
   * catenation of the single-position parts it covers (all flags here generate parts).
   */
  private static void assertCatenationsMatchSpans(String input, List<String> tokens) {
    Map<Integer, String> parts = new HashMap<>();
    List<String[]> multi = new ArrayList<>();
    for (String tok : tokens) {
      int b = tok.lastIndexOf('[');
      String text = tok.substring(0, b);
      String[] se = tok.substring(b + 1, tok.length() - 1).split(",");
      int s = Integer.parseInt(se[0]);
      int e = Integer.parseInt(se[1]);
      if (e - s == 1) {
        if (parts.containsKey(s) == false || text.length() < parts.get(s).length()) {
          parts.put(s, text);
        }
      } else {
        multi.add(new String[] {text, se[0], se[1]});
      }
    }
    for (String[] m : multi) {
      StringBuilder sb = new StringBuilder();
      for (int p = Integer.parseInt(m[1]); p < Integer.parseInt(m[2]); p++) {
        sb.append(parts.get(p));
      }
      assertEquals(
          "input=" + input + " token " + m[0] + "[" + m[1] + "," + m[2] + ") in " + tokens,
          sb.toString(),
          m[0]);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Controls: same shapes with an ASCII first subword. These pass on every version.

  public void testControlAsciiCatenateWords() throws Exception {
    List<String> g = graph(new WordDelimiterGraphFilter(whitespace("x-ab-12"), WORDS, null));
    assertEquals(List.of("xab[0,2)", "x[0,1)", "ab[1,2)", "12[2,3)"), g);
    assertCatenationsMatchSpans("x-ab-12", g);
  }

  public void testControlAsciiCatenateNumbers() throws Exception {
    // no two adjacent number parts: no catenation
    assertEquals(
        List.of("x[0,1)", "ab[1,2)", "12[2,3)"),
        graph(new WordDelimiterGraphFilter(whitespace("x-ab-12"), NUMBERS, null)));
    assertEquals(
        List.of("1[0,1)", "ab[1,2)", "12[2,3)"),
        graph(new WordDelimiterGraphFilter(whitespace("1-ab-12"), NUMBERS, null)));
    // positive control: adjacent number parts are catenated at the last part's position
    assertEquals(
        List.of("x[0,1)", "1234[1,3)", "12[1,2)", "34[2,3)"),
        graph(new WordDelimiterGraphFilter(whitespace("x-12-34"), NUMBERS, null)));
  }

  // ---------------------------------------------------------------------------------------------
  // Bug, CATENATE_WORDS: the catenation of a run started by a supplementary-character subword
  // spans the following number part, which it does not contain.

  public void testSupplementaryLetterCatenateWordsPositionLength() throws Exception {
    String in = CJK_B + "-ab-12";
    List<String> g = graph(new WordDelimiterGraphFilter(whitespace(in), WORDS, null));
    assertEquals(
        List.of(CJK_B + "ab[0,2)", CJK_B + "[0,1)", "ab[1,2)", "12[2,3)"), g);
  }

  public void testControlAsciiCatenateWordsTwoRuns() throws Exception {
    // two word runs separated by a number part: "xab" for the first run, "cd" alone is no run
    assertEquals(
        List.of("xab[0,2)", "x[0,1)", "ab[1,2)", "12[2,3)", "cd[3,4)"),
        graph(new WordDelimiterGraphFilter(whitespace("x-ab-12-cd"), WORDS, null)));
  }

  public void testSupplementaryLetterCatenateWordsAcrossNumberPart() throws Exception {
    // same shape: the number part must end the first run, so "cd" is not joined to it
    String in = CJK_B + "-ab-12-cd";
    assertEquals(
        List.of(CJK_B + "ab[0,2)", CJK_B + "[0,1)", "ab[1,2)", "12[2,3)", "cd[3,4)"),
        graph(new WordDelimiterGraphFilter(whitespace(in), WORDS, null)));
  }

  public void testEmojiCatenateWordsGraphConsistency() throws Exception {
    String in = EMOJI + "-ab-12";
    assertCatenationsMatchSpans(
        in, graph(new WordDelimiterGraphFilter(whitespace(in), WORDS, null)));
  }

  // ---------------------------------------------------------------------------------------------
  // Bug, CATENATE_NUMBERS: a run started by a supplementary-character subword joins a number part
  // that is not adjacent to it (the word part "ab" lies in between).

  public void testSupplementaryLetterCatenateNumbersNotAdjacent() throws Exception {
    String in = CJK_B + "-ab-12";
    assertEquals(
        List.of(CJK_B + "[0,1)", "ab[1,2)", "12[2,3)"),
        graph(new WordDelimiterGraphFilter(whitespace(in), NUMBERS, null)));
  }

  // ---------------------------------------------------------------------------------------------
  // Bug, ALPHANUM entry in the char type table (public constructor, and the factory 'types' file).

  public void testAlphanumTypeTableConstructor() throws Exception {
    byte[] table = WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE.clone();
    table['@'] = (byte) WordDelimiterIterator.ALPHANUM;
    assertEquals(
        List.of("@x[0,1)", "ab[1,2)", "12[2,3)"),
        graph(new WordDelimiterGraphFilter(whitespace("@x-ab-12"), false, table, NUMBERS, null)));
  }

  public void testAlphanumTypeTableFactory() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("catenateWords", "1");
    args.put("types", "types.txt");
    WordDelimiterGraphFilterFactory f = new WordDelimiterGraphFilterFactory(args);
    f.inform(new StringMockResourceLoader("@ => ALPHANUM\n"));
    // default factory flags: generate word and number parts, split on case change and numerics
    String in = "@x-ab-12";
    List<String> g = graph(f.create(whitespace(in)));
    assertCatenationsMatchSpans(in, g);
  }

  // ---------------------------------------------------------------------------------------------
  // User-visible effect at search time: the wrong position length lets a graph phrase query
  // match a document that lacks one of the query's subwords.

  private static Analyzer analyzer(boolean flatten) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String field) {
        Tokenizer t = new WhitespaceTokenizer();
        TokenStream ts = new WordDelimiterGraphFilter(t, WORDS, null);
        if (flatten) {
          ts = new FlattenGraphFilter(ts);
        }
        return new TokenStreamComponents(t, ts);
      }
    };
  }

  private static TreeSet<String> hits(IndexSearcher s, Query q) throws IOException {
    TreeSet<String> ids = new TreeSet<>();
    for (ScoreDoc sd : s.search(q, 100).scoreDocs) {
      ids.add(s.storedFields().document(sd.doc).get("id"));
    }
    return ids;
  }

  public void testGraphPhraseQueryFalsePositive() throws Exception {
    String[] texts = {
      CJK_B + "hot-sale-49", CJK_B + "hot-sale-50", "xhot-sale-49", "xhot-sale-50",
    };
    try (Analyzer indexA = analyzer(true);
        Analyzer queryA = analyzer(false);
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(indexA))) {
        for (int i = 0; i < texts.length; i++) {
          Document d = new Document();
          d.add(new StringField("id", texts[i], Field.Store.YES));
          d.add(new TextField("body", texts[i], Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        QueryBuilder qb = new QueryBuilder(queryA);
        // control: ASCII first subword
        Query qc = qb.createPhraseQuery("body", "xhot-sale-50");
        assertEquals("query " + qc, new TreeSet<>(List.of("xhot-sale-50")), hits(s, qc));
        // same shape with a supplementary letter in the first subword
        Query qb2 = qb.createPhraseQuery("body", CJK_B + "hot-sale-50");
        assertEquals(
            "query " + qb2, new TreeSet<>(List.of(CJK_B + "hot-sale-50")), hits(s, qb2));
      }
    }
  }
}
