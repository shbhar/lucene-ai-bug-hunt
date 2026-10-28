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
import java.text.Normalizer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * ASCIIFoldingFilter folds U+FB06 LATIN SMALL LIGATURE ST to "st" and U+017F LATIN SMALL LETTER
 * LONG S to "s", but has no entry for U+FB05 LATIN SMALL LIGATURE LONG S T, whose compatibility
 * decomposition is also "st". So "faﬅ" stays unfolded and the query "fast" misses it.
 */
public class TestASCIIFoldingLongSTLigature extends BaseTokenStreamTestCase {

  private static final String ST_LIGATURE = "faﬆ"; // U+FB06 LATIN SMALL LIGATURE ST
  private static final String LONG_S = "faſt"; // U+017F LATIN SMALL LETTER LONG S, then t
  private static final String LONG_S_T_LIGATURE = "faﬅ"; // U+FB05 LATIN SMALL LIGATURE LONG S T

  private static Analyzer analyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        TokenStream stream = new LowerCaseFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, new ASCIIFoldingFilter(stream));
      }
    };
  }

  /** Unicode data from the JDK: both ligatures decompose (NFKD) to "st". */
  public void testUnicodeFacts() {
    assertEquals("st", Normalizer.normalize("ﬅ", Normalizer.Form.NFKD));
    assertEquals("st", Normalizer.normalize("ﬆ", Normalizer.Form.NFKD));
  }

  /** The three spellings of "fast" fold to the same term. */
  public void testLongSTLigatureFolds() throws IOException {
    try (Analyzer a = analyzer()) {
      assertAnalyzesTo(a, ST_LIGATURE, new String[] {"fast"}); // control, passes today
      assertAnalyzesTo(a, LONG_S, new String[] {"fast"}); // control, passes today
      assertAnalyzesTo(a, LONG_S_T_LIGATURE, new String[] {"fast"});
    }
  }

  /** The same analyzer at index and query time: the ASCII query "fast" finds all three. */
  public void testAsciiQueryFindsDocument() throws IOException {
    try (Analyzer a = analyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        for (String text : new String[] {ST_LIGATURE, LONG_S, LONG_S_T_LIGATURE}) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        QueryBuilder builder = new QueryBuilder(a);
        assertEquals(
            "hits for the query 'fast'",
            3,
            searcher.count(builder.createBooleanQuery("body", "fast")));
      }
    }
  }
}
