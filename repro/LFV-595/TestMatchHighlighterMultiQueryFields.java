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

import java.io.IOException;
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
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MatchHighlighter#highlight(TopDocs, Query...) with two queries on different fields must highlight
 * the same fields as one query that combines them. Doc 0 is {title: foo, body: bar}, doc 1 is
 * {title: baz, body: bar}; the queries are title:foo and body:bar.
 */
public class TestMatchHighlighterMultiQueryFields extends LuceneTestCase {

  private static final Query TITLE = new TermQuery(new Term("title", "foo"));
  private static final Query BODY = new TermQuery(new Term("body", "bar"));

  /** What highlight(topDocs, title:foo OR body:bar) returns: docId -> (field -> highlights). */
  private static final Map<Integer, Map<String, List<String>>> EXPECTED =
      new TreeMap<>(
          Map.of(
              0, new TreeMap<>(Map.of("title", List.of(">foo<"), "body", List.of(">bar<"))),
              1, new TreeMap<>(Map.of("body", List.of(">bar<")))));

  private interface Check {
    void run(MatchHighlighter highlighter, TopDocs topDocs) throws IOException;
  }

  private void withIndex(Check check) throws IOException {
    try (Analyzer analyzer = new StandardAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        for (String title : List.of("foo", "baz")) {
          Document doc = new Document();
          doc.add(new TextField("title", title, Field.Store.YES));
          doc.add(new TextField("body", "bar", Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(or(TITLE, BODY), 10, Sort.INDEXORDER);
        assertEquals(2, topDocs.scoreDocs.length);
        MatchHighlighter highlighter =
            new MatchHighlighter(searcher, analyzer)
                .appendFieldHighlighter(
                    FieldValueHighlighters.highlighted(
                        80, 1, new PassageFormatter("...", ">", "<"), _ -> true))
                .appendFieldHighlighter(FieldValueHighlighters.skipRemaining());
        // The baseline: one query that touches both fields highlights both fields.
        assertEquals(
            "highlight(topDocs, title OR body)",
            EXPECTED,
            highlight(highlighter, topDocs, or(TITLE, BODY)));
        check.run(highlighter, topDocs);
      }
    }
  }

  private static Query or(Query a, Query b) {
    return new BooleanQuery.Builder()
        .add(a, BooleanClause.Occur.SHOULD)
        .add(b, BooleanClause.Occur.SHOULD)
        .build();
  }

  private static Map<Integer, Map<String, List<String>>> highlight(
      MatchHighlighter highlighter, TopDocs topDocs, Query... queries) throws IOException {
    Map<Integer, Map<String, List<String>>> result = new TreeMap<>();
    highlighter
        .highlight(topDocs, queries)
        .forEach(doc -> result.put(doc.docId, new TreeMap<>(doc.fields)));
    return result;
  }

  public void testTitleQueryThenBodyQuery() throws IOException {
    withIndex(
        (highlighter, topDocs) ->
            assertEquals(
                "highlight(topDocs, title, body)",
                EXPECTED,
                highlight(highlighter, topDocs, TITLE, BODY)));
  }

  public void testBodyQueryThenTitleQuery() throws IOException {
    withIndex(
        (highlighter, topDocs) ->
            assertEquals(
                "highlight(topDocs, body, title)",
                EXPECTED,
                highlight(highlighter, topDocs, BODY, TITLE)));
  }
}
