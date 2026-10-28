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
 * Controls for TestMultiQueryCrossFieldHighlights; all expected to pass on every version: (1)
 * separate queries on the SAME field (the upstream test's pattern); (2) separate queries on
 * different fields when both fields are force-loaded with alwaysFetchFields (root-cause probe: the
 * missing highlights are due to which stored fields are loaded, not to match collection).
 */
public class TestMultiQueryCrossFieldControls extends LuceneTestCase {
  private static final String SUBJECT = "subject";
  private static final String CONTENT = "content";

  private interface Body {
    void run(IndexSearcher searcher, Analyzer analyzer) throws IOException;
  }

  private void withIndex(Body body) throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    try (Analyzer closeMe = analyzer;
        Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document d0 = new Document();
      d0.add(new Field(SUBJECT, "alpha beta", TextField.TYPE_STORED));
      d0.add(new Field(CONTENT, "gamma delta", TextField.TYPE_STORED));
      w.addDocument(d0);
      Document d1 = new Document();
      d1.add(new Field(SUBJECT, "omega", TextField.TYPE_STORED));
      d1.add(new Field(CONTENT, "delta epsilon", TextField.TYPE_STORED));
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

  public void testSeparateQueriesSameField() throws IOException {
    withIndex(
        (searcher, analyzer) -> {
          Query q1 = new TermQuery(new Term(SUBJECT, "alpha"));
          Query q2 = new TermQuery(new Term(SUBJECT, "beta"));
          TopDocs topDocs = searcher.search(or(q1, q2), 10, Sort.INDEXORDER);
          MatchHighlighter hl = highlighter(searcher, analyzer);
          assertEquals(
              Map.of(0, Map.of(SUBJECT, List.of("[alpha] [beta]"))), run(hl, topDocs, q1, q2));
        });
  }

  public void testSeparateQueriesDifferentFieldsWithAlwaysFetch() throws IOException {
    withIndex(
        (searcher, analyzer) -> {
          Query qSubject = new TermQuery(new Term(SUBJECT, "alpha"));
          Query qContent = new TermQuery(new Term(CONTENT, "delta"));
          Query combined = or(qSubject, qContent);
          TopDocs topDocs = searcher.search(combined, 10, Sort.INDEXORDER);
          MatchHighlighter hl = highlighter(searcher, analyzer);
          hl.alwaysFetchFields(SUBJECT, CONTENT);
          Map<Integer, Map<String, List<String>>> expected =
              Map.of(
                  0, Map.of(SUBJECT, List.of("[alpha] beta"), CONTENT, List.of("gamma [delta]")),
                  1, Map.of(CONTENT, List.of("[delta] epsilon")));
          assertEquals(expected, run(hl, topDocs, combined));
          assertEquals(expected, run(hl, topDocs, qSubject, qContent));
          assertEquals(expected, run(hl, topDocs, qContent, qSubject));
        });
  }
}
