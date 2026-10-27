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
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The default UnifiedHighlighter (WEIGHT_MATCHES on) evaluates the query on a reader in which the
 * highlighted field holds only the hit document. A block join whose child query uses that field
 * then finds no children, so the parent hit loses every highlight, including those of its own
 * required clause +text:shirt, although Weight#matches on the searched index reports them.
 */
public class TestUnifiedHighlighterBlockJoinSameField extends LuceneTestCase {

  private static Document doc(FieldType textType, String type, String text, String size) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new Field("text", text, textType));
    d.add(new StringField("size", size, Field.Store.NO));
    return d;
  }

  private void check(FieldType textType, boolean childQueryOnHighlightedField) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocuments(
            List.of(
                doc(textType, "child", "size small", "small"),
                doc(textType, "child", "size large", "large"),
                doc(textType, "parent", "red shirt sale", "none")));
        w.addDocuments(
            List.of(doc(textType, "child", "size small", "small"), doc(textType, "parent", "blue shirt", "none")));
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false, false, false);
        Query child =
            new BooleanQuery.Builder()
                .add(
                    childQueryOnHighlightedField
                        ? new TermQuery(new Term("text", "small"))
                        : new TermQuery(new Term("size", "small")),
                    Occur.MUST)
                .add(new TermQuery(new Term("type", "child")), Occur.FILTER)
                .build();
        Query join =
            new ToParentBlockJoinQuery(
                child, new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))), ScoreMode.None);
        Query q =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("text", "shirt")), Occur.MUST)
                .add(join, Occur.MUST)
                .build();
        TopDocs hits = searcher.search(q, 10, Sort.INDEXORDER);
        assertEquals(2, hits.totalHits.value()); // parents 2 and 4

        // Weight#matches on the searched index reports text:shirt for both parent hits
        LeafReaderContext ctx = reader.leaves().get(0);
        for (int parent : new int[] {2, 4}) {
          Weight weight = searcher.createWeight(searcher.rewrite(q), org.apache.lucene.search.ScoreMode.COMPLETE_NO_SCORES, 1f);
          Matches m = weight.matches(ctx, parent);
          assertNotNull("matches of hit " + parent, m);
          MatchesIterator it = m.getMatches("text");
          assertNotNull(it);
          assertTrue(it.next());
          assertEquals(1, it.startPosition()); // 'shirt'
        }

        String[] expected = {"red <b>shirt</b> sale", "blue <b>shirt</b>"};
        UnifiedHighlighter withoutWeightMatches =
            UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
        assertArrayEquals(expected, withoutWeightMatches.highlight("text", q, hits)); // control
        UnifiedHighlighter defaults = UnifiedHighlighter.builder(searcher, analyzer).build();
        assertArrayEquals(expected, defaults.highlight("text", q, hits));
      }
    }
  }

  private static FieldType offsetsInPostings() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  private static FieldType termVectors() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    ft.setStoreTermVectorOffsets(true);
    ft.freeze();
    return ft;
  }

  /** Default offset source for a stored TextField: re-analysis. */
  public void testReanalysis() throws Exception {
    check(TextField.TYPE_STORED, true);
  }

  /** Offsets in postings: the join is an unrecognized query part, so re-analysis is used too. */
  public void testOffsetsInPostings() throws Exception {
    check(offsetsInPostings(), true);
  }

  public void testTermVectors() throws Exception {
    check(termVectors(), true);
  }

  /** Control: the same join with its child query on another field highlights correctly. */
  public void testControlChildQueryOnOtherField() throws Exception {
    check(TextField.TYPE_STORED, false);
  }
}
