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
package org.apache.lucene.search.uhighlight.visibility;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, outside the uhighlight package): when a hit has no match in
 * a multi-valued field, the UnifiedHighlighter's default summary is documented as "the first
 * maxPassages sentences from the field". The values are joined internally with a separator
 * character; that separator should not become a summary passage of its own.
 */
public class TestMultiValuedNoMatchSummary extends LuceneTestCase {

  private static final char NUL = '\u0000';

  /** Three ways to index the highlighted field: re-analysis, postings offsets, term vectors. */
  private static FieldType[] bodyTypes() {
    FieldType analysis = new FieldType(TextField.TYPE_STORED);
    analysis.freeze();
    FieldType postings = new FieldType(TextField.TYPE_STORED);
    postings.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    postings.freeze();
    FieldType vectors = new FieldType(TextField.TYPE_STORED);
    vectors.setStoreTermVectors(true);
    vectors.setStoreTermVectorPositions(true);
    vectors.setStoreTermVectorOffsets(true);
    vectors.freeze();
    return new FieldType[] {analysis, postings, vectors};
  }

  private static String escape(String s) {
    return s == null ? "null" : s.replace(String.valueOf(NUL), "\\u0000");
  }

  /**
   * Indexes one document whose "body" has the given values and whose "id" is "x", then highlights
   * "body" for the hit of a query on the "id" field (so there is no match in "body").
   */
  private String summarize(FieldType bodyType, int maxPassages, Query query, String... values)
      throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new StringField("id", "x", Field.Store.NO));
      for (String v : values) {
        doc.add(new Field("body", v, bodyType));
      }
      w.addDocument(doc);
      w.commit();
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(r);
        TopDocs top = searcher.search(query, 10);
        assertEquals(1, top.scoreDocs.length);
        UnifiedHighlighter uh = UnifiedHighlighter.builder(searcher, analyzer).build();
        String[] snippets =
            uh.highlightFields(
                    new String[] {"body"},
                    query,
                    new int[] {top.scoreDocs[0].doc},
                    new int[] {maxPassages})
                .get("body");
        assertEquals(1, snippets.length);
        return snippets[0];
      }
    }
  }

  /** Control: a query that matches both values renders them with the ellipsis, no NUL. */
  public void testControlHighlightPathOfTwoValues() throws Exception {
    for (FieldType ft : bodyTypes()) {
      String s =
          summarize(
              ft, 2, new TermQuery(new Term("body", "value")), "First value.", "Second value.");
      assertEquals("First <b>value</b>.... Second <b>value</b>.", s);
    }
  }

  /** Default sentence iterator, two one-sentence values, no match in body, maxPassages = 2. */
  public void testNoMatchSummaryTwoPassages() throws Exception {
    for (FieldType ft : bodyTypes()) {
      String s =
          summarize(
              ft, 2, new TermQuery(new Term("id", "x")), "First value.", "Second value.");
      assertEquals(
          "summary has the internal value separator: " + escape(s), -1, s.indexOf(NUL));
      assertTrue(
          "summary of 2 passages lacks the second sentence: " + escape(s),
          s.contains("Second value."));
    }
  }

  /** Same, maxPassages = 3: two sentences exist, both should be there, still without the NUL. */
  public void testNoMatchSummaryThreePassages() throws Exception {
    for (FieldType ft : bodyTypes()) {
      String s =
          summarize(
              ft, 3, new TermQuery(new Term("id", "x")), "First value.", "Second value.");
      assertEquals(
          "summary has the internal value separator: " + escape(s), -1, s.indexOf(NUL));
    }
  }

  /** Empty first value, maxPassages = 1 (the default of highlight(field, query, topDocs)). */
  public void testNoMatchSummaryEmptyFirstValue() throws Exception {
    for (FieldType ft : bodyTypes()) {
      String s = summarize(ft, 1, new TermQuery(new Term("id", "x")), "", "Only value.");
      assertEquals(
          "summary has the internal value separator: " + escape(s), -1, s.indexOf(NUL));
    }
  }
}
