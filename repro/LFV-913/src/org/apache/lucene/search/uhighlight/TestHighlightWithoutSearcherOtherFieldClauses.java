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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * highlightWithoutSearcher (default flags, so WEIGHT_MATCHES) evaluates the whole query on a
 * one-field MemoryIndex of the given text. A required or filter clause on another field can never
 * match there, so the text gets no highlight at all, although the same text highlights fine with
 * WEIGHT_MATCHES off and through highlight() on an indexed document with the same text.
 */
public class TestHighlightWithoutSearcherOtherFieldClauses extends LuceneTestCase {

  private static final String TEXT = "a b c";

  private final Analyzer analyzer = new StandardAnalyzer();

  /** Reference: highlight() with a searcher over one document with the same body text. */
  private String highlightIndexed(Query query) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        // stored, no offsets: re-analysis (OffsetSource.ANALYSIS), as in highlightWithoutSearcher
        doc.add(new Field("body", TEXT, TextField.TYPE_STORED));
        doc.add(new TextField("title", "x", Field.Store.NO));
        doc.add(new StringField("lang", "en", Field.Store.NO));
        doc.add(new IntPoint("year", 2020));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs hits = searcher.search(query, 1);
        assertEquals("the reference document must match " + query, 1, hits.scoreDocs.length);
        return UnifiedHighlighter.builder(searcher, analyzer).build().highlight("body", query, hits)[0];
      }
    }
  }

  private void check(Query query, String expected) throws Exception {
    check(query, expected, expected);
  }

  /**
   * @param expected what highlight() returns on an indexed copy (and what the default flags should
   *     return here)
   * @param expectedWeightMatchesOff the same with WEIGHT_MATCHES off (phrases marked word by word,
   *     as documented for that flag)
   */
  private void check(Query query, String expected, String expectedWeightMatchesOff)
      throws Exception {
    assertEquals("highlight() on an indexed copy", expected, highlightIndexed(query));
    assertEquals(
        "highlightWithoutSearcher, WEIGHT_MATCHES off",
        expectedWeightMatchesOff,
        UnifiedHighlighter.builderWithoutSearcher(analyzer)
            .withWeightMatches(false)
            .build()
            .highlightWithoutSearcher("body", query, TEXT, 1));
    assertEquals(
        "highlightWithoutSearcher, default flags",
        expected,
        UnifiedHighlighter.builderWithoutSearcher(analyzer)
            .build()
            .highlightWithoutSearcher("body", query, TEXT, 1));
  }

  /** +title:x +body:a */
  public void testRequiredTermOnOtherField() throws Exception {
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("title", "x")), Occur.MUST)
            .add(new TermQuery(new Term("body", "a")), Occur.MUST)
            .build();
    check(q, "<b>a</b> b c");
  }

  /** #lang:en +body:"a b" (a keyword filter next to a phrase) */
  public void testKeywordFilterNextToPhrase() throws Exception {
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("lang", "en")), Occur.FILTER)
            .add(new PhraseQuery("body", "a", "b"), Occur.MUST)
            .build();
    check(q, "<b>a b</b> c", "<b>a</b> <b>b</b> c");
  }

  /** +body:a #year:[2000 TO 2030] (a numeric range filter) */
  public void testPointRangeFilter() throws Exception {
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("body", "a")), Occur.MUST)
            .add(IntPoint.newRangeQuery("year", 2000, 2030), Occur.FILTER)
            .build();
    check(q, "<b>a</b> b c");
  }

  /** Control: the other-field clause is optional, so the text is highlighted. */
  public void testControlOptionalClauseOnOtherField() throws Exception {
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("title", "x")), Occur.SHOULD)
            .add(new TermQuery(new Term("body", "a")), Occur.SHOULD)
            .build();
    check(q, "<b>a</b> b c");
  }
}
