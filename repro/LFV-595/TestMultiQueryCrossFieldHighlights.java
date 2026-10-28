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
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
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
 * MatchHighlighter.highlight(topDocs, q1, q2) with q1 and q2 on different fields: every field that
 * contains a match of one of the passed queries should be highlighted, exactly as when the same
 * clauses are passed as one disjunction. Uses only public API.
 */
public class TestMultiQueryCrossFieldHighlights extends LuceneTestCase {
  private static final String SUBJECT = "subject";
  private static final String CONTENT = "content";

  private static FieldType storedWithOffsets() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  private interface Body {
    void run(IndexSearcher searcher, Analyzer analyzer) throws IOException;
  }

  /** doc 0: both fields match; doc 1: only CONTENT matches. */
  private void withIndex(FieldType type, Body body) throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    try (Analyzer closeMe = analyzer;
        Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document d0 = new Document();
      d0.add(new Field(SUBJECT, "alpha beta", type));
      d0.add(new Field(CONTENT, "gamma delta", type));
      w.addDocument(d0);
      Document d1 = new Document();
      d1.add(new Field(SUBJECT, "omega", type));
      d1.add(new Field(CONTENT, "delta epsilon", type));
      w.addDocument(d1);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        body.run(new IndexSearcher(reader), analyzer);
      }
    }
  }

  private static MatchHighlighter highlighter(IndexSearcher searcher, Analyzer analyzer) {
    return new MatchHighlighter(searcher, analyzer)
        .appendFieldHighlighter(
            FieldValueHighlighters.highlighted(
                80, 1, new PassageFormatter("...", "[", "]"), f -> true))
        .appendFieldHighlighter(FieldValueHighlighters.skipRemaining());
  }

  /** docId -> (field -> formatted values), sorted, so field order does not matter. */
  private static Map<Integer, Map<String, List<String>>> run(
      MatchHighlighter hl, TopDocs topDocs, Query... queries) throws IOException {
    return hl.highlight(topDocs, queries)
        .collect(
            Collectors.toMap(
                h -> h.docId, h -> new TreeMap<>(h.fields), (a, b) -> a, TreeMap::new));
  }

  private static Query or(Query a, Query b) {
    return new BooleanQuery.Builder()
        .add(a, BooleanClause.Occur.SHOULD)
        .add(b, BooleanClause.Occur.SHOULD)
        .build();
  }

  private void check(FieldType type, boolean reversed) throws IOException {
    withIndex(
        type,
        (searcher, analyzer) -> {
          Query qSubject = new TermQuery(new Term(SUBJECT, "alpha"));
          Query qContent = new TermQuery(new Term(CONTENT, "delta"));
          Query combined = or(qSubject, qContent);
          TopDocs topDocs = searcher.search(combined, 10, Sort.INDEXORDER);
          assertEquals(2, topDocs.scoreDocs.length);

          MatchHighlighter hl = highlighter(searcher, analyzer);
          Map<Integer, Map<String, List<String>>> reference = run(hl, topDocs, combined);
          // Sanity: the reference (one combined query) highlights both fields of doc 0 and the
          // content field of doc 1.
          assertEquals(
              "reference (combined query)",
              Map.of(
                  0, Map.of(SUBJECT, List.of("[alpha] beta"), CONTENT, List.of("gamma [delta]")),
                  1, Map.of(CONTENT, List.of("[delta] epsilon"))),
              reference);

          Map<Integer, Map<String, List<String>>> separate =
              reversed
                  ? run(hl, topDocs, qContent, qSubject)
                  : run(hl, topDocs, qSubject, qContent);
          assertEquals(
              "highlight(topDocs, "
                  + (reversed ? "content:delta, subject:alpha" : "subject:alpha, content:delta")
                  + ") vs highlight(topDocs, subject:alpha OR content:delta)",
              reference,
              separate);
        });
  }

  public void testSeparateQueriesOffsetsInPostings() throws IOException {
    check(storedWithOffsets(), false);
  }

  public void testSeparateQueriesOffsetsInPostingsReversed() throws IOException {
    check(storedWithOffsets(), true);
  }

  public void testSeparateQueriesPositionsOnly() throws IOException {
    check(TextField.TYPE_STORED, false);
  }

  public void testSeparateQueriesPositionsOnlyReversed() throws IOException {
    check(TextField.TYPE_STORED, true);
  }
}
