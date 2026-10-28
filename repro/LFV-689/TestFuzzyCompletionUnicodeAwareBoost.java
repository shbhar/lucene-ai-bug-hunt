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
package org.apache.lucene.search.suggest.document;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.TopSuggestDocs.SuggestScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.automaton.Operations;

/**
 * FuzzyCompletionQuery with unicodeAware=true measures edits in code points, but its similarity
 * boost (FuzzyCompletionWeight#setNextMatch) compares the query's code points with the UTF-8 bytes
 * of the matched prefix, so the boost stops at the first non-ASCII character.
 *
 * <p>The two indexes below are the same problem with the letter 'b' renamed to 'é'. In code point
 * mode every letter is one unit, so the results must be the same; they are not.
 */
public class TestFuzzyCompletionUnicodeAwareBoost extends LuceneTestCase {

  private static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = newIndexWriterConfig(random(), analyzer);
    Codec def = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(def.getName(), def) {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return completion;
              }
            };
          }
        });
    return iwc;
  }

  /** Indexes the suggestions, runs a unicode-aware fuzzy query (maxEdits 1), returns key/score. */
  private List<String> suggest(String[] values, int[] weights, String key) throws Exception {
    Analyzer analyzer = new WhitespaceAnalyzer();
    List<String> out = new ArrayList<>();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(analyzer))) {
      for (int i = 0; i < values.length; i++) {
        Document doc = new Document();
        doc.add(new SuggestField("suggest_field", values[i], weights[i]));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        FuzzyCompletionQuery query =
            new FuzzyCompletionQuery(
                analyzer,
                new Term("suggest_field", key),
                null,
                1, // maxEdits
                true, // transpositions
                1, // nonFuzzyPrefix
                3, // minFuzzyLength
                true, // unicodeAware
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
        for (SuggestScoreDoc hit : searcher.suggest(query, 10, false).scoreLookupDocs()) {
          out.add(hit.key + "/" + hit.score);
        }
      }
    }
    return out;
  }

  public void testAsciiExactPrefixIsBoostedAboveTypo() throws Exception {
    // control: "abcd" shares the prefix "ab" with the minimal accepted prefix (2*3 = 6),
    // the typo "axcd" shares only "a" (1*4 = 4)
    assertEquals(
        List.of("abcd/6.0", "axcd/4.0"),
        suggest(new String[] {"abcd", "axcd"}, new int[] {3, 4}, "abc"));
  }

  public void testNonAsciiExactPrefixIsBoostedLikeAscii() throws Exception {
    // the same problem with 'b' renamed to 'é' (one code point): same expected result
    assertEquals(
        List.of("aécd/6.0", "axcd/4.0"),
        suggest(new String[] {"aécd", "axcd"}, new int[] {3, 4}, "aéc"));
  }

  public void testCjkExactPrefixIsBoosted() throws Exception {
    // every character is non-ASCII: the exact prefix gets no boost at all
    // expected like the ASCII case: "日本語" (exact, minimal prefix "日本" -> boost 2 -> 1*2)
    // above the typo "日大語" (minimal prefix "日大語" shares "日" -> boost 1 -> 1*1)
    assertEquals(
        List.of("日本語/2.0", "日大語/1.0"),
        suggest(new String[] {"日本語", "日大語"}, new int[] {1, 1}, "日本語"));
  }
}
