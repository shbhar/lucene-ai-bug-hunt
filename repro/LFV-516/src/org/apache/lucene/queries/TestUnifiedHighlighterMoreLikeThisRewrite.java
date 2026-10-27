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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.mlt.MoreLikeThisQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** MoreLikeThisQuery (default minDocFreq 5) rewritten against the highlighter's one-doc reader. */
public class TestUnifiedHighlighterMoreLikeThisRewrite extends LuceneTestCase {

  public void testMoreLikeThisDefaults() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (int i = 0; i < 6; i++) {
          Document d = new Document();
          d.add(new TextField("text", "brown fox number" + i, Field.Store.YES));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false, false, false);
        MoreLikeThisQuery q = new MoreLikeThisQuery("fox", new String[] {"text"}, analyzer, "text");
        TopDocs hits = searcher.search(q, 10, Sort.INDEXORDER);
        assertEquals(6, hits.scoreDocs.length);
        LeafReaderContext ctx = reader.leaves().get(0);
        Weight weight = searcher.createWeight(searcher.rewrite(q), org.apache.lucene.search.ScoreMode.COMPLETE_NO_SCORES, 1f);
        Matches m = weight.matches(ctx, 0);
        assertNotNull(m);
        assertNotNull(m.getMatches("text")); // 'fox' on the searched index
        UnifiedHighlighter defaults = UnifiedHighlighter.builder(searcher, analyzer).build();
        String[] snippets = defaults.highlight("text", q, hits);
        assertEquals("brown <b>fox</b> number0", snippets[0]);
      }
    }
  }
}
