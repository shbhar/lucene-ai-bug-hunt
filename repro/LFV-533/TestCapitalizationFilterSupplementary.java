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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;

/**
 * CapitalizationFilter changes case one UTF-16 char at a time, so cased letters outside the BMP
 * (Adlam, Osage, Deseret, ...) keep their case, while BMP letters in the same token are mapped.
 */
public class TestCapitalizationFilterSupplementary extends BaseTokenStreamTestCase {

  // Adlam alif and daali, Osage a, Deseret long i and long e: capital / small letter pairs
  private static final int ADLAM_ALIF = 0x1E900, ADLAM_ALIF_SMALL = 0x1E922;
  private static final int ADLAM_DAALI = 0x1E901, ADLAM_DAALI_SMALL = 0x1E923;
  private static final int OSAGE_A = 0x104B0, OSAGE_A_SMALL = 0x104D8;
  private static final int DESERET_I = 0x10400, DESERET_I_SMALL = 0x10428;
  private static final int DESERET_E = 0x10401, DESERET_E_SMALL = 0x10429;

  private static String s(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  /** All-ASCII text as is; otherwise one item per code point, ASCII letters as themselves. */
  private static String show(String text) {
    if (text.chars().allMatch(c -> c < 128)) {
      return text;
    }
    List<String> items = new ArrayList<>();
    text.codePoints()
        .forEach(
            cp ->
                items.add(
                    Character.isLetter(cp) && cp < 128
                        ? String.valueOf((char) cp)
                        : String.format(Locale.ROOT, "U+%04X", cp)));
    return String.join(" ", items);
  }

  /** Runs one token (KeywordTokenizer) through the given filter. */
  private static String capitalize(String text, Function<Tokenizer, TokenStream> filter)
      throws IOException {
    Tokenizer tokenizer = new KeywordTokenizer();
    tokenizer.setReader(new StringReader(text));
    TokenStream ts = filter.apply(tokenizer);
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    assertTrue(ts.incrementToken());
    String out = term.toString();
    assertFalse(ts.incrementToken());
    ts.end();
    ts.close();
    return out;
  }

  private static final Function<Tokenizer, TokenStream> DEFAULTS = CapitalizationFilter::new;

  /** Controls: the filter works on BMP letters, and the JDK maps the letters used below. */
  public void testControls() throws IOException {
    assertEquals("Kitten", capitalize("kiTTEN", DEFAULTS));
    assertEquals("Hey you", capitalize("hEy yOU", DEFAULTS));
    assertEquals(ADLAM_ALIF, Character.toUpperCase(ADLAM_ALIF_SMALL));
    assertEquals(ADLAM_DAALI_SMALL, Character.toLowerCase(ADLAM_DAALI));
    assertEquals(OSAGE_A_SMALL, Character.toLowerCase(OSAGE_A));
    assertEquals(DESERET_I_SMALL, Character.toLowerCase(DESERET_I));
  }

  /** Default settings: first letter capital, the rest (and later words) lower case. */
  public void testDefaultSettings() throws IOException {
    String[][] cases = {
      // first letter not capitalized (CapitalizationFilter.java:190)
      {s(ADLAM_ALIF_SMALL, ADLAM_DAALI_SMALL), s(ADLAM_ALIF, ADLAM_DAALI_SMALL)},
      // rest of the word not lower-cased (:193)
      {s(ADLAM_ALIF, ADLAM_DAALI), s(ADLAM_ALIF, ADLAM_DAALI_SMALL)},
      // the BMP letters of the same token are mapped, the Osage capital is not
      {s('a', 'B', OSAGE_A), s('A', 'b', OSAGE_A_SMALL)},
      // a later word, lower-cased because onlyFirstWord is true by default (:152)
      {
        s('h', 'E', 'y', ' ', DESERET_I, DESERET_E),
        s('H', 'e', 'y', ' ', DESERET_I_SMALL, DESERET_E_SMALL)
      }
    };
    List<String> wrong = new ArrayList<>();
    for (String[] c : cases) {
      String actual = capitalize(c[0], DEFAULTS);
      if (!actual.equals(c[1])) {
        wrong.add(show(c[0]) + " -> " + show(actual) + ", expected " + show(c[1]));
      }
    }
    assertTrue(String.join("\n", wrong), wrong.isEmpty());
  }

  /** A keep word with forceFirstLetter=true gets a capital first letter (:159). */
  public void testKeepWordForceFirstLetter() throws IOException {
    String word = s(DESERET_I_SMALL, 'x');
    CharArraySet keep = new CharArraySet(List.of(word), false);
    String actual =
        capitalize(
            word,
            t ->
                new CapitalizationFilter(
                    t,
                    true,
                    keep,
                    true,
                    null,
                    0,
                    CapitalizationFilter.DEFAULT_MAX_WORD_COUNT,
                    CapitalizationFilter.DEFAULT_MAX_TOKEN_LENGTH));
    assertEquals(show(s(DESERET_I, 'x')), show(actual));
  }

  /**
   * End to end: a field analyzed with KeywordTokenizer + CapitalizationFilter, as for facet values.
   * Two spellings of a Latin word become one term; two spellings of an Adlam word stay two terms
   * (neither is the capitalized form), so a query in one spelling finds 1 of the 2 documents.
   */
  public void testIndexedTermsAndSearch() throws IOException {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new KeywordTokenizer();
            return new TokenStreamComponents(tokenizer, new CapitalizationFilter(tokenizer));
          }
        };
    String adlamUpper = s(ADLAM_ALIF, ADLAM_DAALI);
    String adlamLower = s(ADLAM_ALIF_SMALL, ADLAM_DAALI_SMALL);
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, analyzer);
    for (String text : new String[] {"PARIS", "paris", adlamUpper, adlamLower}) {
      Document doc = new Document();
      doc.add(new TextField("city", text, Field.Store.NO));
      writer.addDocument(doc);
    }
    IndexReader reader = writer.getReader();
    writer.close();
    List<String> terms = new ArrayList<>();
    TermsEnum te = MultiTerms.getTerms(reader, "city").iterator();
    for (BytesRef t = te.next(); t != null; t = te.next()) {
      terms.add(show(t.utf8ToString()));
    }
    IndexSearcher searcher = newSearcher(reader);
    QueryBuilder qb = new QueryBuilder(analyzer);
    String actual =
        "terms="
            + terms
            + " hits(paris)="
            + searcher.count(qb.createBooleanQuery("city", "paris"))
            + " hits("
            + show(adlamLower)
            + ")="
            + searcher.count(qb.createBooleanQuery("city", adlamLower));
    reader.close();
    dir.close();
    analyzer.close();
    assertEquals("terms=[Paris, U+1E900 U+1E923] hits(paris)=2 hits(U+1E922 U+1E923)=2", actual);
  }
}
