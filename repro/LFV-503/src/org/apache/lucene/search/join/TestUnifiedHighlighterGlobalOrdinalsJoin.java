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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
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
import org.apache.lucene.util.BytesRef;

/**
 * The default UnifiedHighlighter (WEIGHT_MATCHES on) throws IllegalStateException for any query
 * that contains a JoinUtil global-ordinals join: it re-creates the query's Weight on its own
 * IndexSearcher over the hit's leaf, and GlobalOrdinalsQuery / GlobalOrdinalsWithScoreQuery reject
 * every searcher other than the one the join was built with.
 */
public class TestUnifiedHighlighterGlobalOrdinalsJoin extends LuceneTestCase {

  private static Document doc(String type, String join, String text) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new SortedDocValuesField("join", new BytesRef(join)));
    d.add(new TextField("text", text, Field.Store.YES));
    return d;
  }

  public void testHighlightGlobalOrdinalsJoinHit() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc("from", "k1", "great review")); // doc 0
        w.addDocument(doc("to", "k1", "red shirt")); // doc 1
        w.addDocument(doc("to", "k2", "blue shirt")); // doc 2
        w.forceMerge(1); // single segment: no OrdinalMap needed
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false, false, false);
        StringBuilder failures = new StringBuilder();
        for (ScoreMode scoreMode : ScoreMode.values()) {
          Query join =
              JoinUtil.createJoinQuery(
                  "join",
                  new TermQuery(new Term("text", "great")),
                  new TermQuery(new Term("type", "to")),
                  searcher,
                  scoreMode,
                  null);
          Query q =
              new BooleanQuery.Builder()
                  .add(new TermQuery(new Term("text", "shirt")), Occur.MUST)
                  .add(join, Occur.MUST)
                  .build();
          TopDocs hits = searcher.search(q, 10);
          assertEquals(1, hits.scoreDocs.length);
          assertEquals(1, hits.scoreDocs[0].doc);
          String[] expected = {"red <b>shirt</b>"};
          UnifiedHighlighter withoutWeightMatches =
              UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
          assertArrayEquals(expected, withoutWeightMatches.highlight("text", q, hits)); // control
          UnifiedHighlighter defaults = UnifiedHighlighter.builder(searcher, analyzer).build();
          try {
            String[] actual = defaults.highlight("text", q, hits);
            if (!java.util.Arrays.equals(expected, actual)) {
              failures.append(scoreMode).append(": ").append(java.util.Arrays.toString(actual)).append('\n');
            }
          } catch (IllegalStateException e) {
            failures.append(scoreMode).append(": throws ").append(e).append('\n');
          }
        }
        assertEquals("default UnifiedHighlighter on a global-ordinals join hit", "", failures.toString());
      }
    }
  }

  /**
   * Same design, silent variant: ParentChildrenBlockJoinQuery takes the parent's top-level doc id,
   * which the highlighter's single-leaf searcher reads as a doc id of the hit's segment.
   */
  public void testHighlightParentChildrenHitInSecondSegment() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocuments(java.util.List.of(doc("parent", "k0", "first")));
        w.commit(); // segment 1: doc 0
        w.addDocuments(java.util.List.of(doc("child", "k1", "size small"), doc("parent", "k1", "red shirt")));
        w.addDocuments(java.util.List.of(doc("child", "k2", "size large"), doc("parent", "k2", "blue shirt")));
        w.commit(); // segment 2: docs 1-4
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.leaves().size());
        IndexSearcher searcher = newSearcher(reader, false, false, false);
        Query children =
            new ParentChildrenBlockJoinQuery(
                new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))),
                new TermQuery(new Term("type", "child")),
                2); // top-level doc id of the parent "red shirt"
        Query q =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("text", "size")), Occur.MUST)
                .add(children, Occur.MUST)
                .build();
        TopDocs hits = searcher.search(q, 10, Sort.INDEXORDER);
        assertEquals(1, hits.scoreDocs.length);
        assertEquals(1, hits.scoreDocs[0].doc); // "size small"
        String[] expected = {"<b>size</b> small"};
        UnifiedHighlighter withoutWeightMatches =
            UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
        assertArrayEquals(expected, withoutWeightMatches.highlight("text", q, hits)); // control
        UnifiedHighlighter defaults = UnifiedHighlighter.builder(searcher, analyzer).build();
        assertArrayEquals(expected, defaults.highlight("text", q, hits));
      }
    }
  }
}
