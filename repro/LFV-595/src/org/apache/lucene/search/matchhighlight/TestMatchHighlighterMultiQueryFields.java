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
package org.apache.lucene.search.matchhighlight;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MatchHighlighter#highlight(TopDocs, Query...) highlights each query's matches; a later query whose
 * matches are on a field that the first query does not touch loses its highlights.
 */
public class TestMatchHighlighterMultiQueryFields extends LuceneTestCase {

  private static Map<String, List<String>> highlight(
      MatchHighlighter highlighter, TopDocs topDocs, Query... queries) throws Exception {
    return new TreeMap<>(highlighter.highlight(topDocs, queries).findFirst().orElseThrow().fields);
  }

  public void testLaterQueryOnAnotherFieldIsHighlighted() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("title", "foo", Field.Store.YES));
        doc.add(new TextField("body", "bar", Field.Store.YES));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);

        MatchHighlighter highlighter =
            new MatchHighlighter(searcher, analyzer)
                .appendFieldHighlighter(
                    FieldValueHighlighters.highlighted(
                        80, 1, new PassageFormatter("...", ">", "<"), _ -> true))
                .appendFieldHighlighter(FieldValueHighlighters.skipRemaining());

        Query title = new TermQuery(new Term("title", "foo"));
        Query body = new TermQuery(new Term("body", "bar"));
        Query both =
            new BooleanQuery.Builder()
                .add(title, BooleanClause.Occur.SHOULD)
                .add(body, BooleanClause.Occur.SHOULD)
                .build();

        TopDocs topDocs = searcher.search(both, 10);
        assertEquals(1, topDocs.scoreDocs.length);

        Map<String, List<String>> expected = Map.of("body", List.of(">bar<"), "title", List.of(">foo<"));

        // One query that touches both fields: both fields are highlighted.
        assertEquals(expected, highlight(highlighter, topDocs, both));

        // The same two clauses passed as separate queries (as the class's own test does to tell
        // the queries' markers apart) must give the same highlighted fields.
        assertEquals(
            "highlight(topDocs, title, body)", expected, highlight(highlighter, topDocs, title, body));
        assertEquals(
            "highlight(topDocs, body, title)", expected, highlight(highlighter, topDocs, body, title));
      }
    }
  }
}
