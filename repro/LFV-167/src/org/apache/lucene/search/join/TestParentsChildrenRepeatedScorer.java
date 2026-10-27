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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ParentsChildrenBlockJoinWeight#scorerSupplier throws IllegalStateException the second time it
 * is called for the same LeafReaderContext (a guard meant to reject intra-segment concurrency).
 * Weight#scorer's javadoc allows repeated requests, and BooleanWeight#explain makes one, so
 * explain() of a BooleanQuery that contains the join query throws.
 */
public class TestParentsChildrenRepeatedScorer extends LuceneTestCase {

  private DirectoryReader reader;
  private Directory dir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));
    List<Document> block = new ArrayList<>();
    for (int i = 0; i < 2; i++) { // docs 0, 1: children
      Document c = new Document();
      c.add(new StringField("type", "child", Field.Store.NO));
      c.add(new StringField("color", "red", Field.Store.NO));
      block.add(c);
    }
    Document p = new Document(); // doc 2: parent
    p.add(new StringField("type", "parent", Field.Store.NO));
    p.add(new StringField("name", "shoe", Field.Store.NO));
    block.add(p);
    w.addDocuments(block);
    w.forceMerge(1);
    reader = DirectoryReader.open(w);
    w.close();
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static Query joinQuery() {
    return new ParentsChildrenBlockJoinQuery(
        new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))),
        new TermQuery(new Term("name", "shoe")),
        new TermQuery(new Term("color", "red")));
  }

  /** IndexSearcher#explain of a two-clause BooleanQuery containing the join query. */
  public void testExplainOfBooleanQuery() throws Exception {
    IndexSearcher searcher = new IndexSearcher(reader);
    Query bq =
        new BooleanQuery.Builder()
            .add(joinQuery(), Occur.SHOULD)
            .add(new TermQuery(new Term("color", "blue")), Occur.SHOULD)
            .build();
    assertEquals(2, searcher.search(bq, 10).totalHits.value());
    assertTrue(searcher.explain(bq, 0).isMatch());
  }

  /** Weight#scorer javadoc: "may be requested multiple times as part of a single search call". */
  public void testScorerRequestedTwiceForSameLeaf() throws Exception {
    IndexSearcher searcher = new IndexSearcher(reader);
    Weight weight =
        searcher.createWeight(
            searcher.rewrite(joinQuery()), org.apache.lucene.search.ScoreMode.COMPLETE, 1f);
    LeafReaderContext leaf = reader.leaves().get(0);
    assertNotNull(weight.scorer(leaf));
    assertNotNull(weight.scorer(leaf));
  }
}
