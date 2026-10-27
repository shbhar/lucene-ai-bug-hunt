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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.search.join.ToChildBlockJoinQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * User-visible effect of the inherited FilterWeight#matches: the UnifiedHighlighter (which uses
 * Weight#matches by default, DEFAULT_ENABLE_WEIGHT_MATCHES = true) highlights nothing in a hit of a
 * query that has a ToChildBlockJoinQuery clause, because the conjunction's Matches is null.
 */
public class TestUnifiedHighlighterWithToChildJoin extends LuceneTestCase {

  public void testHighlightConjunctionWithToChildClause() throws IOException {
    FieldType withOffsets = new FieldType(TextField.TYPE_STORED);
    withOffsets.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document child = new Document();
        child.add(new StringField("type", "child", Field.Store.NO));
        child.add(new Field("body", "small cotton shirt", withOffsets));
        Document parent = new Document();
        parent.add(new StringField("type", "parent", Field.Store.NO));
        parent.add(new StringField("color", "red", Field.Store.NO));
        w.addDocuments(List.of(child, parent));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q =
            new BooleanQuery.Builder()
                .add(
                    new ToChildBlockJoinQuery(
                        new TermQuery(new Term("color", "red")),
                        new QueryBitSetProducer(new TermQuery(new Term("type", "parent")))),
                    Occur.MUST)
                .add(new TermQuery(new Term("body", "shirt")), Occur.MUST)
                .build();
        TopDocs hits = searcher.search(q, 10);
        assertEquals(1, hits.totalHits.value());
        assertEquals(0, hits.scoreDocs[0].doc);
        UnifiedHighlighter uh = UnifiedHighlighter.builder(searcher, analyzer).build();
        String[] snippets = uh.highlight("body", q, hits);
        assertEquals("small cotton <b>shirt</b>", snippets[0]);
      }
    }
  }
}
