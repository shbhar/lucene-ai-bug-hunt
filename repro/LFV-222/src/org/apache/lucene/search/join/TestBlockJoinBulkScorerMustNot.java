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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
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
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A MUST_NOT clause next to a scoring ToParentBlockJoinQuery must exclude parents. Every block is
 * children + parent; every other parent is "discontinued" and must not match
 * {@code +join(color:red) -status:discontinued}.
 */
public class TestBlockJoinBulkScorerMustNot extends LuceneTestCase {

  private static final int BLOCKS = 3000;

  private Directory buildIndex(int childrenPerBlock) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int b = 0; b < BLOCKS; b++) {
        List<Document> block = new ArrayList<>();
        for (int c = 0; c < childrenPerBlock; c++) {
          Document child = new Document();
          child.add(new StringField("color", "red", Field.Store.NO));
          block.add(child);
        }
        Document parent = new Document();
        parent.add(new StringField("type", "parent", Field.Store.NO));
        parent.add(new StoredField("block", b));
        if (b % 2 == 0) {
          parent.add(new StringField("status", "discontinued", Field.Store.NO));
        }
        block.add(parent);
        w.addDocuments(block);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static Query query() {
    BitSetProducer parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
    Query join =
        new ToParentBlockJoinQuery(new TermQuery(new Term("color", "red")), parents, ScoreMode.Avg);
    return new BooleanQuery.Builder()
        .add(join, Occur.MUST)
        .add(new TermQuery(new Term("status", "discontinued")), Occur.MUST_NOT)
        .build();
  }

  /** Blocks of 4 docs: 4096-doc windows never split a block. */
  public void testExcludedParentsAreNotReturned() throws IOException {
    try (Directory dir = buildIndex(3);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setQueryCache(null);
      Query q = query();
      int expected = BLOCKS / 2;
      // count() does not need scores and takes a path that applies the MUST_NOT clause
      assertEquals(expected, searcher.count(q));
      // A scoring search, with exact hit counting
      TopDocs td = searcher.search(q, new TopScoreDocCollectorManager(BLOCKS, Integer.MAX_VALUE));
      int excluded = 0;
      for (ScoreDoc sd : td.scoreDocs) {
        int block = reader.storedFields().document(sd.doc).getField("block").numericValue().intValue();
        if (block % 2 == 0) {
          excluded++;
        }
      }
      assertEquals("hits that match the MUST_NOT clause", 0, excluded);
      assertEquals(expected, td.totalHits.value());
    }
  }

  /** The plain top-10 search (TOP_SCORES) also returns excluded parents. */
  public void testTopHitsDoNotMatchMustNot() throws IOException {
    try (Directory dir = buildIndex(3);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setQueryCache(null);
      TopDocs td = searcher.search(query(), 10);
      assertTrue(td.scoreDocs.length > 0);
      for (ScoreDoc sd : td.scoreDocs) {
        int block = reader.storedFields().document(sd.doc).getField("block").numericValue().intValue();
        assertTrue("hit " + sd.doc + " (block " + block + ") matches the MUST_NOT clause", block % 2 == 1);
      }
    }
  }

  /** Blocks of 3 docs: some blocks start before a 4096-doc window and end inside it. */
  public void testBlocksAcrossWindowBoundaries() throws IOException {
    try (Directory dir = buildIndex(2);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setQueryCache(null);
      TopDocs td = searcher.search(query(), new TopScoreDocCollectorManager(BLOCKS, Integer.MAX_VALUE));
      assertEquals(BLOCKS / 2, td.totalHits.value());
    }
  }
}
