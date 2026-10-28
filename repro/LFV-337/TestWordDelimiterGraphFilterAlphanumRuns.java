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

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.CATENATE_NUMBERS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.CATENATE_WORDS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.QueryBuilder;

/**
 * A catenation run that starts with a subword typed ALPHA|DIGIT (a character mapped to ALPHANUM in
 * the type table, or any supplementary character) must still end at a subword it does not absorb.
 */
public class TestWordDelimiterGraphFilterAlphanumRuns extends BaseTokenStreamTestCase {

  private static final int WORDS = GENERATE_WORD_PARTS | GENERATE_NUMBER_PARTS | CATENATE_WORDS;
  private static final int NUMBERS = GENERATE_WORD_PARTS | GENERATE_NUMBER_PARTS | CATENATE_NUMBERS;

  /** U+20000, a CJK Extension B letter (outside the BMP). */
  private static final String CJK = new String(Character.toChars(0x20000));

  /** The default table with '#' typed ALPHANUM, so that a hashtag stays one subword. */
  private static byte[] hashtagTable() {
    byte[] table = WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE.clone();
    table['#'] = WordDelimiterIterator.ALPHANUM;
    return table;
  }

  private static TokenStream wdgf(String text, byte[] table, int flags) {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(text));
    return new WordDelimiterGraphFilter(tokenizer, false, table, flags, null);
  }

  public void testCatenateWordsRunEndsBeforeNumberPart() throws Exception {
    byte[] table = hashtagTable();
    // control: "xhotsale" covers the positions of "xhot" and "sale" only
    assertTokenStreamContents(
        wdgf("xhot-sale-50", table, WORDS),
        new String[] {"xhotsale", "xhot", "sale", "50"},
        null,
        null,
        new int[] {1, 0, 1, 1},
        new int[] {2, 1, 1, 1},
        12);
    // "#hotsale" must not cover the position of "50"
    assertTokenStreamContents(
        wdgf("#hot-sale-50", table, WORDS),
        new String[] {"#hotsale", "#hot", "sale", "50"},
        null,
        null,
        new int[] {1, 0, 1, 1},
        new int[] {2, 1, 1, 1},
        12);
    // the same with the default table, for a subword that starts with a supplementary character
    assertTokenStreamContents(
        wdgf(CJK + "hot-sale-50", WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE, WORDS),
        new String[] {CJK + "hotsale", CJK + "hot", "sale", "50"},
        null,
        null,
        new int[] {1, 0, 1, 1},
        new int[] {2, 1, 1, 1},
        13);
  }

  public void testCatenateNumbersJoinsOnlyAdjacentParts() throws Exception {
    byte[] table = hashtagTable();
    // control: a single number part, nothing to catenate
    assertTokenStreamContents(
        wdgf("x-ab-12", table, NUMBERS),
        new String[] {"x", "ab", "12"},
        null,
        null,
        new int[] {1, 1, 1},
        new int[] {1, 1, 1},
        7);
    // "#x" and "12" are not adjacent ("ab" lies between them): no "#x12"
    assertTokenStreamContents(
        wdgf("#x-ab-12", table, NUMBERS),
        new String[] {"#x", "ab", "12"},
        null,
        null,
        new int[] {1, 1, 1},
        new int[] {1, 1, 1},
        8);
    assertTokenStreamContents(
        wdgf(CJK + "-ab-12", WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE, NUMBERS),
        new String[] {CJK, "ab", "12"},
        null,
        null,
        new int[] {1, 1, 1},
        new int[] {1, 1, 1},
        8);
  }

  public void testFactoryTypesOption() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("catenateWords", "1");
    args.put("types", "types.txt");
    WordDelimiterGraphFilterFactory factory = new WordDelimiterGraphFilterFactory(args);
    // '#' as an escape: a types-file line that starts with '#' is a comment
    factory.inform(new StringMockResourceLoader("\\u0023 => ALPHANUM\n"));
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader("#hot-sale-50"));
    assertTokenStreamContents(
        factory.create(tokenizer),
        new String[] {"#hotsale", "#hot", "sale", "50"},
        null,
        null,
        new int[] {1, 0, 1, 1},
        new int[] {2, 1, 1, 1},
        12);
  }

  private static Analyzer analyzer(byte[] table, boolean flatten) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream stream = new WordDelimiterGraphFilter(tokenizer, false, table, WORDS, null);
        return new TokenStreamComponents(
            tokenizer, flatten ? new FlattenGraphFilter(stream) : stream);
      }
    };
  }

  private static Set<String> hits(IndexSearcher searcher, Query query) throws IOException {
    Set<String> ids = new TreeSet<>();
    StoredFields storedFields = searcher.storedFields();
    for (ScoreDoc hit : searcher.search(query, 10).scoreDocs) {
      ids.add(storedFields.document(hit.doc).get("id"));
    }
    return ids;
  }

  public void testPhraseQueryRequiresEveryPart() throws Exception {
    byte[] table = hashtagTable();
    String[] texts = {"#hot-sale-50", "#hot-sale-49", "#hot-sale", "xhot-sale-50", "xhot-sale-49"};
    try (Analyzer indexAnalyzer = analyzer(table, true);
        Analyzer queryAnalyzer = analyzer(table, false);
        Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(indexAnalyzer))) {
        for (String text : texts) {
          Document doc = new Document();
          doc.add(new StringField("id", text, Field.Store.YES));
          doc.add(new TextField("body", text, Field.Store.NO));
          writer.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        QueryBuilder builder = new QueryBuilder(queryAnalyzer);
        // control
        Query query = builder.createPhraseQuery("body", "xhot-sale-50");
        assertEquals(query.toString(), Set.of("xhot-sale-50"), hits(searcher, query));
        query = builder.createPhraseQuery("body", "#hot-sale-50");
        assertEquals(query.toString(), Set.of("#hot-sale-50"), hits(searcher, query));
      }
    }
  }
}
