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
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Weight#matches must return null for every document that does not match the query (Weight.java:
 * "Returns Matches for a specific document, or null if the document does not match the parent
 * query"). ParentsChildrenBlockJoinQuery's Weight returns parentQuery's or childQuery's Matches
 * for any document either of them matches, without checking the join.
 */
public class TestParentsChildrenMatchesContract extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;

  private static Document doc(String type, String text) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new TextField("text", text, Field.Store.YES));
    return d;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      // block 1: docs 0, 1 are children of parent doc 2
      w.addDocuments(List.of(doc("child", "size small"), doc("child", "size large"), doc("parent", "red shirt")));
      // block 2: doc 3 is a child of parent doc 4
      w.addDocuments(List.of(doc("child", "size small"), doc("parent", "blue shirt")));
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Children with text:size of parents with text:red, at most one child per parent. */
  private static Query join() {
    return new ParentsChildrenBlockJoinQuery(
        new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))),
        new TermQuery(new Term("text", "red")),
        new TermQuery(new Term("text", "size")),
        1);
  }

  private static List<Integer> hits(IndexSearcher searcher, Query q) throws IOException {
    List<Integer> hits = new ArrayList<>();
    for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) hits.add(sd.doc);
    hits.sort(null);
    return hits;
  }

  private static List<Integer> docsWithMatches(IndexSearcher searcher, Query q) throws IOException {
    LeafReaderContext ctx = searcher.getIndexReader().leaves().get(0);
    List<Integer> docs = new ArrayList<>();
    for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
      // a fresh Weight per document (the Weight of this query accepts one scorer per leaf)
      Weight w = searcher.createWeight(searcher.rewrite(q), org.apache.lucene.search.ScoreMode.COMPLETE_NO_SCORES, 1f);
      Matches m = w.matches(ctx, doc);
      if (m != null) docs.add(doc);
    }
    return docs;
  }

  /** Only doc 0 is a hit; doc 1 is over the limit, doc 2 is a parent, doc 3 has a blue parent. */
  public void testMatchesOnlyForHits() throws Exception {
    IndexSearcher searcher = newSearcher(reader, false, false, false);
    assertEquals(List.of(0), hits(searcher, join()));
    assertEquals("docs with non-null Weight#matches", List.of(0), docsWithMatches(searcher, join()));
  }

  /** As a MUST_NOT clause the spurious Matches make the hits of the enclosing query lose theirs. */
  public void testMustNotClause() throws Exception {
    IndexSearcher searcher = newSearcher(reader, false, false, false);
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("text", "size")), Occur.MUST)
            .add(join(), Occur.MUST_NOT)
            .build();
    assertEquals(List.of(1, 3), hits(searcher, q));
    assertEquals("docs with non-null Weight#matches", List.of(1, 3), docsWithMatches(searcher, q));
  }

  /** The test framework's own Matches oracle (QueryUtils.check runs it) rejects both queries. */
  public void testCheckHitsCheckMatches() throws Exception {
    IndexSearcher searcher = newSearcher(reader, false, false, false);
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("text", "size")), Occur.MUST)
            .add(join(), Occur.MUST_NOT)
            .build();
    CheckHits.checkMatches(q, searcher);
  }
}
