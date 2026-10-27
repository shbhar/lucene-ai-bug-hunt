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
package org.apache.lucene.queries;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The default UnifiedHighlighter (WEIGHT_MATCHES on) rewrites the query again on its own
 * per-leaf (or one-document re-analysis) searcher. CommonTermsQuery decides at rewrite time,
 * from document frequencies, which terms are required; on the highlighter's reader 'the' looks
 * rare, becomes required, and a hit that lacks 'the' loses all its highlights.
 */
public class TestUnifiedHighlighterCommonTermsRewrite extends LuceneTestCase {

  private static final String[] TEXTS = {
    "the red fox", "the cat", "the dog", "the bird", "quick brown fox", "the quick cow"
  };

  private void check(FieldType ft, boolean twoSegments) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < TEXTS.length; i++) {
          Document d = new Document();
          d.add(new Field("text", TEXTS[i], ft));
          w.addDocument(d);
          if (twoSegments && i == 3) w.commit(); // segment 1: docs 0-3, segment 2: docs 4-5
        }
        if (twoSegments == false) w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false, false, false);
        // 'the' is in 5 of 6 docs (high frequency, optional); 'fox' in 2 (low frequency, required)
        CommonTermsQuery q = new CommonTermsQuery(Occur.SHOULD, Occur.MUST, 0.5f);
        q.add(new Term("text", "the"));
        q.add(new Term("text", "fox"));
        TopDocs hits = searcher.search(q, 10, Sort.INDEXORDER);
        assertEquals(2, hits.scoreDocs.length);
        assertEquals(4, hits.scoreDocs[1].doc); // "quick brown fox"

        // Weight#matches on the searched index reports 'fox' for doc 4
        LeafReaderContext ctx = reader.leaves().get(ReaderUtil.subIndex(4, reader.leaves()));
        Weight weight = searcher.createWeight(searcher.rewrite(q), org.apache.lucene.search.ScoreMode.COMPLETE_NO_SCORES, 1f);
        Matches m = weight.matches(ctx, 4 - ctx.docBase);
        assertNotNull(m);
        assertNotNull(m.getMatches("text"));

        String[] expected = {"<b>the</b> red <b>fox</b>", "quick brown <b>fox</b>"};
        UnifiedHighlighter withoutWeightMatches =
            UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
        assertArrayEquals(expected, withoutWeightMatches.highlight("text", q, hits)); // control
        UnifiedHighlighter defaults = UnifiedHighlighter.builder(searcher, analyzer).build();
        assertArrayEquals(expected, defaults.highlight("text", q, hits));
      }
    }
  }

  /** Stored TextField without offsets: the highlighter re-analyzes the hit into a one-doc index. */
  public void testReanalysisSingleSegment() throws Exception {
    check(TextField.TYPE_STORED, false);
  }

  /** Offsets in postings, two segments: the highlighter rewrites against the hit's segment only. */
  public void testOffsetsInPostingsTwoSegments() throws Exception {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    check(ft, true);
  }

  /** Control: offsets in postings, one segment (same statistics as the search): passes. */
  public void testControlOffsetsInPostingsSingleSegment() throws Exception {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    check(ft, false);
  }
}
