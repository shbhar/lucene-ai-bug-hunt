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
package org.apache.lucene.spatial.composite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.util.ShapeValuesPredicate;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

/**
 * CompositeVerifyQuery.visit calls visitor.visitLeaf(this) and never visits the index query that it
 * rewrites and executes, so term extraction and IndexSearcher's clause limit do not see it.
 */
public class TestCompositeVerifyQueryVisit extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  private static ShapeValuesPredicate everywhere() {
    return new ShapeValuesPredicate(
        new SerializedDVStrategy(CTX, "shape").makeShapeValueSource(),
        SpatialOperation.Intersects,
        CTX.getShapeFactory().rect(-10, 10, -10, 10));
  }

  /** A disjunction of n TermQuery clauses on field "body". */
  private static Query disjunction(int n) {
    List<Query> clauses = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      clauses.add(new TermQuery(new Term("body", "t" + i)));
    }
    return new DisjunctionMaxQuery(clauses, 0f);
  }

  public void testTermCollectorSeesIndexQueryTerms() {
    Query indexQuery = new TermQuery(new Term("body", "a"));
    Set<Term> bare = new HashSet<>();
    indexQuery.visit(QueryVisitor.termCollector(bare));
    assertEquals(Set.of(new Term("body", "a")), bare);

    Set<Term> wrapped = new HashSet<>();
    new CompositeVerifyQuery(indexQuery, everywhere()).visit(QueryVisitor.termCollector(wrapped));
    assertEquals("visit must recurse into the executed index query", bare, wrapped);
  }

  public void testClauseLimitCountsIndexQueryClauses() throws IOException {
    int saved = IndexSearcher.getMaxClauseCount();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("body", "t0", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        IndexSearcher.setMaxClauseCount(8);
        Query indexQuery = disjunction(20);
        // the same 20 clauses as a sibling FILTER clause are rejected
        Query sibling =
            new BooleanQuery.Builder()
                .add(new CompositeVerifyQuery(new TermQuery(new Term("body", "t0")), everywhere()),
                    BooleanClause.Occur.MUST)
                .add(indexQuery, BooleanClause.Occur.FILTER)
                .build();
        expectThrows(IndexSearcher.TooManyClauses.class, () -> searcher.rewrite(sibling));
        expectThrows(IndexSearcher.TooManyClauses.class, () -> searcher.rewrite(indexQuery));

        // ... but not when they are the executed index query of a CompositeVerifyQuery
        Query wrapped = new CompositeVerifyQuery(indexQuery, everywhere());
        expectThrows(
            IndexSearcher.TooManyClauses.class,
            "the 20 executed index-query clauses must count toward maxClauseCount=8",
            () -> searcher.rewrite(wrapped));
      } finally {
        IndexSearcher.setMaxClauseCount(saved);
      }
    }
  }
}
