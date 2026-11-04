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
package org.apache.lucene.search;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndriAndQueryMaxScore extends LuceneTestCase {

  private static class OneDocIndriQuery extends Query {
    final int doc;

    OneDocIndriQuery(int doc) {
      this.doc = doc;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
      return new Weight(this) {
        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext ctx) {
          DocIdSetIterator it = DocIdSetIterator.all(ctx.reader().maxDoc());
          return new DefaultScorerSupplier(
              new IndriScorer(1f) {
                @Override
                public DocIdSetIterator iterator() {
                  return it;
                }

                @Override
                public int docID() {
                  return it.docID();
                }

                @Override
                public float score() {
                  return smoothingScore(it.docID());
                }

                @Override
                public float smoothingScore(int d) {
                  return d == doc ? 100f : 0f;
                }

                @Override
                public float getMaxScore(int upTo) {
                  return 100f;
                }
              });
        }

        @Override
        public Explanation explain(LeafReaderContext ctx, int d) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
          return false;
        }
      };
    }

    @Override
    public String toString(String field) {
      return "oneDoc(" + doc + ")";
    }

    @Override
    public void visit(QueryVisitor visitor) {}

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  public void testTopHitNotPruned() throws Exception {
    final int last = 9999;
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i <= last; i++) {
          w.addDocument(new Document());
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query indri =
            new IndriAndQuery(
                List.of(
                    new BooleanClause(new OneDocIndriQuery(last), Occur.SHOULD),
                    new BooleanClause(new OneDocIndriQuery(last), Occur.SHOULD)));
        Query query =
            new BooleanQuery.Builder()
                .add(indri, Occur.SHOULD)
                .add(new MatchAllDocsQuery(), Occur.SHOULD)
                .build();
        // expected top hit: doc 9999 (score 101); actual: doc 0 (score 1), as getMaxScore is 0
        assertEquals(last, new IndexSearcher(reader).search(query, 1).scoreDocs[0].doc);
      }
    }
  }
}
