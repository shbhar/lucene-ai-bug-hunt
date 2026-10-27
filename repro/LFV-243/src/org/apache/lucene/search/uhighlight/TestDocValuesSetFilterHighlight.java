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

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The UnifiedHighlighter (default flags, WEIGHT_MATCHES) highlights nothing when the query has a
 * doc-values-only set filter built with SortedSetDocValuesField#newSlowSetQuery, because that
 * clause's Weight#matches returns null for matching documents. The same filter written as a
 * doc-values range query highlights normally.
 */
public class TestDocValuesSetFilterHighlight extends LuceneTestCase {

  private String highlight(Query filter) throws Exception {
    return highlight(filter, true);
  }

  /**
   * @param offsets index offsets in postings (POSTINGS offset source); false = a plain TextField
   *     with positions only (ANALYSIS offset source, the default for TextField)
   */
  private String highlight(Query filter, boolean offsets) throws Exception {
    try (Directory dir = newDirectory()) {
      Analyzer analyzer = new MockAnalyzer(random());
      FieldType body = new FieldType(TextField.TYPE_STORED);
      body.setIndexOptions(
          offsets
              ? IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
              : IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("body", "a ripe fruit in a basket", body));
        doc.add(new SortedSetDocValuesField("tags", new BytesRef("banana")));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("body", "fruit")), BooleanClause.Occur.MUST)
                .add(filter, BooleanClause.Occur.FILTER)
                .build();
        TopDocs td = searcher.search(q, 10);
        assertEquals(1, td.totalHits.value());
        UnifiedHighlighter uh = UnifiedHighlighter.builder(searcher, analyzer).build();
        String[] snippets = uh.highlight("body", q, td);
        return snippets[0];
      }
    }
  }

  public void testControlRangeFilter() throws Exception {
    String s =
        highlight(
            SortedSetDocValuesField.newSlowRangeQuery(
                "tags", new BytesRef("banana"), new BytesRef("banana"), true, true));
    assertNotNull(s);
    assertTrue(s, s.contains("<b>fruit</b>"));
  }

  public void testSetFilter() throws Exception {
    String s = highlight(SortedSetDocValuesField.newSlowSetQuery("tags", List.of(new BytesRef("banana"))));
    assertNotNull("no snippet for a matching document", s);
    assertTrue("query term not highlighted: " + s, s.contains("<b>fruit</b>"));
  }

  public void testControlRangeFilterAnalysisOffsets() throws Exception {
    String s =
        highlight(
            SortedSetDocValuesField.newSlowRangeQuery(
                "tags", new BytesRef("banana"), new BytesRef("banana"), true, true),
            false);
    assertNotNull(s);
    assertTrue(s, s.contains("<b>fruit</b>"));
  }

  public void testSetFilterAnalysisOffsets() throws Exception {
    String s =
        highlight(
            SortedSetDocValuesField.newSlowSetQuery("tags", List.of(new BytesRef("banana"))), false);
    assertNotNull("no snippet for a matching document", s);
    assertTrue("query term not highlighted: " + s, s.contains("<b>fruit</b>"));
  }
}
