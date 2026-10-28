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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.IndriAndQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The UnifiedHighlighter in term-extraction mode (withWeightMatches(false)) finds the query terms
 * with QueryVisitor.termCollector (UnifiedHighlighter#extractTerms). It finds none in an
 * IndriAndQuery, so the matching document comes back without highlights, while the BooleanQuery
 * over the same clauses is highlighted. Public API only.
 */
public class TestIndriAndQueryHighlight extends LuceneTestCase {

  public void testTermExtractionHighlightsIndriClauses() throws Exception {
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
        Query bool = new BooleanQuery.Builder().add(clauses.get(0)).add(clauses.get(1)).build();
        Query indri = new IndriAndQuery(clauses);
        UnifiedHighlighter highlighter =
            UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
        String expected = "<b>president</b> <b>washington</b> was the first leader";

        TopDocs boolHits = searcher.search(bool, 10);
        assertEquals(1, boolHits.scoreDocs.length);
        assertEquals(
            "control: BooleanQuery", expected, highlighter.highlight("body", bool, boolHits)[0]);

        TopDocs indriHits = searcher.search(indri, 10);
        assertEquals("the IndriAndQuery matches the document", 1, indriHits.scoreDocs.length);
        assertEquals(
            "IndriAndQuery(body:president, body:washington)",
            expected,
            highlighter.highlight("body", indri, indriHits)[0]);
      }
    }
  }
}
