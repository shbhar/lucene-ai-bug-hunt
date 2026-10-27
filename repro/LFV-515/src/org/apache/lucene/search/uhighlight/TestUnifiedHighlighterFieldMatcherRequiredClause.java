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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * With a field matcher that accepts other fields (requireFieldMatch=false), the WEIGHT_MATCHES
 * path makes every accepted field read the highlighted field's terms. A required clause on another
 * field (a keyword filter, or a term of another text field) then fails on the highlighted field's
 * terms, the whole query's Matches is null and the hit loses every highlight, including the match of
 * its own clause on the highlighted field.
 */
public class TestUnifiedHighlighterFieldMatcherRequiredClause extends LuceneTestCase {

  private void check(FieldType textType, Query q) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document d = new Document();
        d.add(new Field("text", "red shirt sale", textType));
        d.add(new Field("title", "summer collection", textType));
        d.add(new StringField("status", "active", Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false, false, false);
        TopDocs hits = searcher.search(q, 10);
        assertEquals(1, hits.scoreDocs.length);
        String[] expected = {"red <b>shirt</b> sale"};
        UnifiedHighlighter withoutWeightMatches =
            UnifiedHighlighter.builder(searcher, analyzer)
                .withFieldMatcher(field -> true)
                .withWeightMatches(false)
                .build();
        assertArrayEquals(expected, withoutWeightMatches.highlight("text", q, hits)); // control
        UnifiedHighlighter requireFieldMatch = UnifiedHighlighter.builder(searcher, analyzer).build();
        assertArrayEquals(expected, requireFieldMatch.highlight("text", q, hits)); // control
        UnifiedHighlighter anyField =
            UnifiedHighlighter.builder(searcher, analyzer).withFieldMatcher(field -> true).build();
        assertArrayEquals(expected, anyField.highlight("text", q, hits));
      }
    }
  }

  private static Query filtered() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("text", "shirt")), Occur.MUST)
        .add(new TermQuery(new Term("status", "active")), Occur.FILTER)
        .build();
  }

  private static Query twoFields() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("title", "summer")), Occur.MUST)
        .add(new TermQuery(new Term("text", "shirt")), Occur.MUST)
        .build();
  }

  private static FieldType offsets() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  public void testKeywordFilterReanalysis() throws Exception {
    check(TextField.TYPE_STORED, filtered());
  }

  public void testKeywordFilterOffsetsInPostings() throws Exception {
    check(offsets(), filtered());
  }

  public void testRequiredTermOfAnotherTextField() throws Exception {
    check(offsets(), twoFields());
  }
}
