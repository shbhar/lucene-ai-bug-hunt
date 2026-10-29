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
package org.apache.lucene.search.uhighlight;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * When a hit has no match in a multi-valued field, the default summary is "the first maxPassages
 * sentences from the field". The values are joined internally with {@link
 * UnifiedHighlighter#MULTIVAL_SEP_CHAR}; that separator is not a sentence of the field and must not
 * appear in the summary or take the place of a sentence.
 */
public class TestUnifiedHighlighterMultiValuedSummary extends LuceneTestCase {

  /** Matches the document, but nothing in "body", so "body" gets the default summary. */
  private static final Query NO_MATCH_IN_BODY = new TermQuery(new Term("id", "1"));

  /** Indexes one document with the given "body" values and highlights "body" for its hit. */
  private String highlight(Query query, int maxPassages, String... values) throws IOException {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.NO));
        for (String value : values) {
          doc.add(new TextField("body", value, Field.Store.YES));
        }
        iw.addDocument(doc);
      }
      try (IndexReader ir = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(ir);
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.scoreDocs.length);
        UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
        String snippet = highlighter.highlight("body", query, topDocs, maxPassages)[0];
        // print the invisible U+0000 separator as an escape in failure messages
        return snippet == null ? null : snippet.replace("\u0000", "\\u0000");
      }
    }
  }

  public void testSummaryOnePassage() throws IOException {
    assertEquals("First value.", highlight(NO_MATCH_IN_BODY, 1, "First value.", "Second value."));
  }

  public void testSummaryTwoPassages() throws IOException {
    // before the fix: "First value." + U+0000, the separator takes the second sentence's place
    assertEquals(
        "First value.... Second value.",
        highlight(NO_MATCH_IN_BODY, 2, "First value.", "Second value."));
  }

  public void testSummaryMorePassagesThanSentences() throws IOException {
    // before the fix: "First value." + U+0000 + "Second value."
    assertEquals(
        "First value.... Second value.",
        highlight(NO_MATCH_IN_BODY, 3, "First value.", "Second value."));
  }

  public void testSummaryEmptyFirstValue() throws IOException {
    // maxPassages 1, as used by highlight(field, query, topDocs); before the fix: U+0000 alone
    assertEquals("Second value.", highlight(NO_MATCH_IN_BODY, 1, "", "Second value."));
  }

  public void testHighlightedValuesAreJoinedWithEllipsis() throws IOException {
    // control: with a match in both values, the formatter's ellipsis separates them
    assertEquals(
        "First <b>value</b>.... Second <b>value</b>.",
        highlight(new TermQuery(new Term("body", "value")), 2, "First value.", "Second value."));
  }
}
