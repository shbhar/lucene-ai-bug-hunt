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
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Weight#matches must be non-null exactly for the documents the query matches. The index has two
 * blocks:
 *
 * <pre>
 *   doc 0  child   "size small"
 *   doc 1  child   "size large"
 *   doc 2  parent  "red shirt"
 *   doc 3  child   "size small"
 *   doc 4  parent  "blue shirt"
 * </pre>
 */
public class TestParentsChildrenBlockJoinMatches extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  private static Document doc(String type, String text) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new TextField("text", text, Field.Store.NO));
    return d;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      w.addDocuments(
          List.of(
              doc("child", "size small"), doc("child", "size large"), doc("parent", "red shirt")));
      w.addDocuments(List.of(doc("child", "size small"), doc("parent", "blue shirt")));
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Children with text:size of parents with text:red. */
  private static Query childrenOfRedParents(int childLimitPerParent) {
    return new ParentsChildrenBlockJoinQuery(
        new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))),
        new TermQuery(new Term("text", "red")),
        new TermQuery(new Term("text", "size")),
        childLimitPerParent);
  }

  private List<Integer> hits(Query q) throws IOException {
    List<Integer> hits = new ArrayList<>();
    for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
      hits.add(sd.doc);
    }
    hits.sort(null);
    return hits;
  }

  private List<Integer> docsWithMatches(Query q) throws IOException {
    Weight weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
    LeafReaderContext context = reader.leaves().get(0);
    List<Integer> docs = new ArrayList<>();
    for (int doc = 0; doc < context.reader().maxDoc(); doc++) {
      if (weight.matches(context, doc) != null) {
        docs.add(doc);
      }
    }
    return docs;
  }

  /** The parent doc 2 and the child doc 3 of the blue parent are not hits. */
  public void testMatchesOnlyForHits() throws IOException {
    Query join = childrenOfRedParents(ParentsChildrenBlockJoinQuery.DEFAULT_CHILD_LIMIT_PER_PARENT);
    assertEquals(List.of(0, 1), hits(join));
    assertEquals(List.of(0, 1), docsWithMatches(join));
  }

  /** With childLimitPerParent = 1, doc 1 is over the limit. */
  public void testMatchesRespectChildLimitPerParent() throws IOException {
    Query join = childrenOfRedParents(1);
    assertEquals(List.of(0), hits(join));
    assertEquals(List.of(0), docsWithMatches(join));
  }

  private static Query sizeButNotChildOfRedParent() {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("text", "size")), Occur.MUST)
        .add(
            childrenOfRedParents(ParentsChildrenBlockJoinQuery.DEFAULT_CHILD_LIMIT_PER_PARENT),
            Occur.MUST_NOT)
        .build();
  }

  /** As a MUST_NOT clause, the join must not take the Matches away from the hit doc 3. */
  public void testMustNotClause() throws IOException {
    Query q = sizeButNotChildOfRedParent();
    assertEquals(List.of(3), hits(q));
    assertEquals(List.of(3), docsWithMatches(q));
  }

  /** The test framework's own Matches check (run by QueryUtils.check). */
  public void testCheckHitsCheckMatches() throws IOException {
    CheckHits.checkMatches(sizeButNotChildOfRedParent(), searcher);
  }
}
