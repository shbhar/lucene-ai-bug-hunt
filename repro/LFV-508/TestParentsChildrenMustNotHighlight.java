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
package org.apache.lucene.search.join;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Impact: the default UnifiedHighlighter (WEIGHT_MATCHES on) drops the highlights of the hits. */
public class TestParentsChildrenMustNotHighlight extends LuceneTestCase {

  private static Document doc(String type, String text) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new TextField("text", text, Field.Store.YES));
    return d;
  }

  public void testMustNotParentsChildrenKeepsHighlights() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocuments(List.of(doc("child", "size small"), doc("child", "size large"), doc("parent", "red shirt")));
        w.addDocuments(List.of(doc("child", "size small"), doc("parent", "blue shirt")));
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false, false, false);
        Query join =
            new ParentsChildrenBlockJoinQuery(
                new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))),
                new TermQuery(new Term("text", "red")),
                new TermQuery(new Term("text", "size")),
                1);
        Query q =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("text", "size")), Occur.MUST)
                .add(join, Occur.MUST_NOT)
                .build();
        TopDocs hits = searcher.search(q, 10, Sort.INDEXORDER);
        assertEquals(2, hits.totalHits.value()); // docs 1 and 3
        UnifiedHighlighter noWeightMatches =
            UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
        String[] expected = {"<b>size</b> large", "<b>size</b> small"};
        assertArrayEquals(expected, noWeightMatches.highlight("text", q, hits)); // control
        UnifiedHighlighter defaults = UnifiedHighlighter.builder(searcher, analyzer).build();
        assertArrayEquals(expected, defaults.highlight("text", q, hits));
      }
    }
  }
}
