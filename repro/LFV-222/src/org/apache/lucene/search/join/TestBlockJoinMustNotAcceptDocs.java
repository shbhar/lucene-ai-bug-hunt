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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A MUST_NOT clause next to a scoring ToParentBlockJoinQuery must exclude parent documents. Since
 * ReqExclBulkScorer passes the exclusion down as the {@code acceptDocs} of the required clause's
 * BulkScorer, and ToParentBlockJoinQuery's BlockJoinBulkScorer forwards {@code acceptDocs} to the
 * child BulkScorer (child doc space), the exclusion is applied to child documents instead.
 */
public class TestBlockJoinMustNotAcceptDocs extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private BitSetProducer parents;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // block 1: docs 0,1 = children, doc 2 = parent "p1" (visible)
      // block 2: docs 3,4 = children, doc 5 = parent "p2" (hidden)
      for (String[] block :
          new String[][] {{"p1", "visible", "java", "python"}, {"p2", "hidden", "java", "go"}}) {
        List<Document> docs = new ArrayList<>();
        for (int i = 2; i < 4; i++) {
          Document child = new Document();
          child.add(new StringField("skill", block[i], Field.Store.NO));
          docs.add(child);
        }
        Document parent = new Document();
        parent.add(new StringField("type", "parent", Field.Store.NO));
        parent.add(new StringField("id", block[0], Field.Store.YES));
        parent.add(new StringField("status", block[1], Field.Store.NO));
        docs.add(parent);
        w.addDocuments(docs);
      }
    }
    reader = DirectoryReader.open(dir);
    assertEquals(1, reader.leaves().size());
    searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
    CheckJoinIndex.check(reader, parents); // a valid block-join index
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private List<String> hits(Query q) throws Exception {
    TopDocs td = searcher.search(q, 10);
    List<String> ids = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) {
      ids.add(searcher.storedFields().document(sd.doc).get("id"));
    }
    return ids;
  }

  /** A parent-level exclusion must remove the excluded parent. */
  public void testParentLevelMustNotIsHonoured() throws Exception {
    for (ScoreMode sm : new ScoreMode[] {ScoreMode.Avg, ScoreMode.Max, ScoreMode.Min, ScoreMode.Total}) {
      Query join = new ToParentBlockJoinQuery(new TermQuery(new Term("skill", "java")), parents, sm);
      assertEquals(sm.toString(), List.of("p1", "p2"), hits(join));
      Query q =
          new BooleanQuery.Builder()
              .add(join, Occur.MUST)
              .add(new TermQuery(new Term("status", "hidden")), Occur.MUST_NOT)
              .build();
      // the same exclusion through a non-bulk path, for reference:
      assertEquals(1, searcher.count(q));
      assertEquals("ScoreMode." + sm, List.of("p1"), hits(q));
    }
  }

  /**
   * A clause that only matches child documents never matches a parent, so excluding it from a
   * parent-level query must not change the result.
   */
  public void testChildLevelMustNotDoesNotRemoveParents() throws Exception {
    Query join =
        new ToParentBlockJoinQuery(
            new TermQuery(new Term("skill", "java")), parents, ScoreMode.Avg);
    Query q =
        new BooleanQuery.Builder()
            .add(join, Occur.MUST)
            .add(new TermQuery(new Term("skill", "java")), Occur.MUST_NOT)
            .build();
    assertEquals(2, searcher.count(q));
    assertEquals(List.of("p1", "p2"), hits(q));
  }

  /**
   * ReqExclBulkScorer scores in windows of 4096 docs. A block that straddles a window boundary makes
   * BlockJoinBulkScorer score children below the window start, and those children are looked up in
   * the window's acceptDocs.
   */
  public void testBlockStraddlingExclusionWindow() throws Exception {
    try (Directory bigDir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(bigDir, iwc)) {
        for (int b = 0; b < 2000; b++) { // blocks of 3 docs: doc 4095 and 4096 share a block
          List<Document> docs = new ArrayList<>();
          for (int c = 0; c < 2; c++) {
            Document child = new Document();
            child.add(new StringField("skill", "java", Field.Store.NO));
            docs.add(child);
          }
          Document parent = new Document();
          parent.add(new StringField("type", "parent", Field.Store.NO));
          parent.add(new StringField("status", b % 2 == 0 ? "visible" : "hidden", Field.Store.NO));
          docs.add(parent);
          w.addDocuments(docs);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(bigDir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        CheckJoinIndex.check(r, parents);
        Query join =
            new ToParentBlockJoinQuery(
                new TermQuery(new Term("skill", "java")), parents, ScoreMode.Avg);
        Query q =
            new BooleanQuery.Builder()
                .add(join, Occur.MUST)
                .add(new TermQuery(new Term("status", "hidden")), Occur.MUST_NOT)
                .build();
        assertEquals(1000, s.count(q));
        assertEquals(1000, s.search(q, 2000).totalHits.value());
      }
    }
  }
}
