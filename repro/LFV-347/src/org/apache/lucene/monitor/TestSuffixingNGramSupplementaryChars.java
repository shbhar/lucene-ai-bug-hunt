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
package org.apache.lucene.monitor;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RegexpQueryHandler selects a regexp query for a document only if the document's token stream,
 * passed through RegexpQueryHandler.wrapTermStream (SuffixingNGramTokenFilter), contains the
 * query's static substring plus the ngram suffix. SuffixingNGramTokenFilter starts each token with a
 * gram size equal to the token's UTF-16 length but bounds grams by its code point count, so a token
 * that contains a supplementary character (emoji, CJK Extension B, historic scripts) produces no
 * ngrams at all, and every regexp query that should match it is silently skipped.
 *
 * <p>Uses only public API (Monitor, TermFilteredPresearcher, RegexpQueryHandler, MemoryIndex).
 */
public class TestSuffixingNGramSupplementaryChars extends LuceneTestCase {

  private static final String FIELD = "text";
  private static final String EMOJI = new String(Character.toChars(0x1F600)); // grinning face
  private static final String GOTHIC_AHSA = new String(Character.toChars(0x10330));
  private static final String GOTHIC_BAIRKAN = new String(Character.toChars(0x10331));

  public void testRegexpQueryMatchesTokenWithSupplementaryCharacter() throws IOException {
    // {pattern, document text}: each pattern matches the document's only token
    String[][] cases = {
      {"hel.*", "hello"}, // control: BMP-only token
      {"hel.*", "hello" + EMOJI},
      {GOTHIC_AHSA + ".*", GOTHIC_AHSA + GOTHIC_BAIRKAN},
    };
    Analyzer analyzer = new WhitespaceAnalyzer();
    List<String> missed = new ArrayList<>();
    try (Monitor monitor =
        new Monitor(
            analyzer,
            new TermFilteredPresearcher(
                TermWeightor.DEFAULT, List.of(new RegexpQueryHandler()), Set.of()))) {
      for (String[] c : cases) {
        RegexpQuery query = new RegexpQuery(new Term(FIELD, c[0]));
        Document doc = new Document();
        doc.add(new TextField(FIELD, c[1], Field.Store.NO));
        assertTrue(
            c[0] + " must match " + c[1],
            MemoryIndex.fromDocument(doc, analyzer).createSearcher().count(query) > 0);
        monitor.clear();
        monitor.register(new MonitorQuery("q", query));
        if (monitor.match(doc, QueryMatch.SIMPLE_MATCHER).matches("q") == null) {
          missed.add("/" + c[0] + "/ on \"" + c[1] + "\"");
        }
      }
    }
    assertTrue("Monitor.match missed matching regexp queries: " + missed, missed.isEmpty());
  }

  public void testNgramsAreEmittedForTokensWithSupplementaryCharacters() throws IOException {
    String token = "a" + EMOJI;
    WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(token));
    Set<String> emitted = new HashSet<>();
    try (TokenStream ts = new RegexpQueryHandler().wrapTermStream(FIELD, tokenizer)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        emitted.add(term.toString());
      }
      ts.end();
    }
    String sfx = RegexpQueryHandler.DEFAULT_NGRAM_SUFFIX;
    // every substring of the token, suffixed (what a BMP-only token of the same length gets)
    Set<String> expected = Set.of(token, token + sfx, "a" + sfx, EMOJI + sfx, sfx);
    assertEquals(expected, emitted);
  }
}
