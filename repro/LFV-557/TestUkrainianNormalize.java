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
package org.apache.lucene.analysis.uk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Analyzer#normalize must return "the representation that it would have in the index". The query
 * parsers send prefix, wildcard, regexp, fuzzy and range terms through it.
 * UkrainianMorfologikAnalyzer lower-cases and char-maps (apostrophes, stress mark, soft hyphen, ґ)
 * at index time but overrides neither normalize(String, TokenStream) nor
 * initReaderForNormalization.
 */
public class TestUkrainianNormalize extends BaseTokenStreamTestCase {

  /** Shows invisible and punctuation code points (U+2019, U+0301, U+00AD) as escapes. */
  private static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c > 127 && Character.isLetter(c) == false) {
        sb.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Words whose indexed form is a single token: the lower-cased, char-mapped surface form. */
  public void testNormalizeReturnsIndexedForm() throws Exception {
    String[][] cases = {
      {"Lucene", "lucene"}, // upper case
      {"м\u2019ясо", "м'ясо"}, // typographic apostrophe U+2019
      {"м'я\u0301со", "м'ясо"}, // stress mark U+0301
      {"сек\u00ADрет", "секрет"}, // soft hyphen U+00AD
      {"ґрунт", "грунт"}, // ґ
    };
    List<String> mismatches = new ArrayList<>();
    try (Analyzer a = new UkrainianMorfologikAnalyzer()) {
      for (String[] c : cases) {
        assertAnalyzesTo(a, c[0], new String[] {c[1]}); // the indexed form, checked first
        String normalized = a.normalize("body", c[0]).utf8ToString();
        if (normalized.equals(c[1]) == false) {
          mismatches.add(esc(c[0]) + " -> " + esc(normalized) + " (index holds " + c[1] + ")");
        }
      }
    }
    assertTrue("normalize differs from the indexed form: " + mismatches, mismatches.isEmpty());
  }

  /**
   * A prefix query built the way QueryParserBase#getPrefixQuery builds it, from the analyzer's
   * normalize(), for a prefix typed exactly as the document spells it.
   */
  public void testPrefixQueryAsBuiltByTheQueryParsers() throws Exception {
    try (Analyzer a = new UkrainianMorfologikAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "м\u2019ясо Lucene", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        List<String> misses = new ArrayList<>();
        // controls (already in the indexed form), then the document's own spelling
        for (String prefix : new String[] {"м'яс", "luc", "м\u2019яс", "Luc"}) {
          BytesRef term = a.normalize("body", prefix);
          int hits = s.count(new PrefixQuery(new Term("body", term)));
          if (hits != 1) {
            misses.add(esc(prefix) + "* -> body:" + esc(term.utf8ToString()) + "* hits=" + hits);
          }
        }
        assertTrue("prefix queries miss the document: " + misses, misses.isEmpty());
      }
    }
  }
}
