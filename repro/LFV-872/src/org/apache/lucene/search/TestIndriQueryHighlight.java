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
package org.apache.lucene.search;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Manifestation of the leaf-only IndriQuery#visit in the highlighter module: the UnifiedHighlighter
 * (term-extraction mode, withWeightMatches(false)) extracts the query terms with
 * QueryVisitor.termCollector (UnifiedHighlighter#extractTerms), finds none in an IndriAndQuery and
 * highlights nothing, while the BooleanQuery over the same clauses is highlighted. (The classic
 * Highlighter is not used here: it leaves unknown query classes to the extractUnknownQuery hook by
 * design. The weight-matches mode depends on Weight#matches, which IndriAndWeight does not
 * implement; it is reported for information only.)
 */
public class TestIndriQueryHighlight extends LuceneTestCase {

  public void testHighlightersSeeIndriClauseTerms() throws Exception {
    String text = "president washington was the first leader";
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      FieldType ft = new FieldType(TextField.TYPE_STORED);
      ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("body", text, ft));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        List<BooleanClause> clauses =
            List.of(
                new BooleanClause(new TermQuery(new Term("body", "president")), Occur.SHOULD),
                new BooleanClause(new TermQuery(new Term("body", "washington")), Occur.SHOULD));
        Query indri = new IndriAndQuery(clauses);
        Query bool = new BooleanQuery.Builder().add(clauses.get(0)).add(clauses.get(1)).build();

        StringBuilder sb = new StringBuilder();
        for (Query q : new Query[] {bool, indri}) {
          TopDocs td = searcher.search(q, 10);
          assertEquals(1, td.scoreDocs.length);
          String terms =
              UnifiedHighlighter.builder(searcher, analyzer)
                  .withWeightMatches(false)
                  .build()
                  .highlight("body", q, td)[0];
          String weightMatches =
              UnifiedHighlighter.builder(searcher, analyzer)
                  .withWeightMatches(true)
                  .build()
                  .highlight("body", q, td)[0];
          sb.append('\n')
              .append(q.getClass().getSimpleName())
              .append(": termExtraction=")
              .append(terms)
              .append(" (weightMatches=")
              .append(weightMatches)
              .append(')');
        }
        String report = sb.toString();
        String expected = "<b>president</b> <b>washington</b> was the first leader";
        System.out.println("HIGHLIGHT" + report);
        assertTrue(
            "the UnifiedHighlighter must mark the IndriAndQuery's matching terms:" + report,
            report.contains("IndriAndQuery: termExtraction=" + expected));
      }
    }
  }
}
