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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestReqExclSkipsExcludedRun extends LuceneTestCase {

  public void testRequiredClauseSkipsExcludedRun() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 10_000; i++) {
        Document doc = new Document();
        if (i < 9_000) {
          doc.add(new StringField("grp", "old", Field.Store.NO));
        }
        w.addDocument(doc);
      }
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        int[] moves = new int[1];
        Query all =
            new Query() {
              @Override
              public Weight createWeight(IndexSearcher s, ScoreMode scoreMode, float boost) {
                return new ConstantScoreWeight(this, boost) {
                  @Override
                  public ScorerSupplier scorerSupplier(LeafReaderContext ctx) {
                    DocIdSetIterator it = DocIdSetIterator.all(ctx.reader().maxDoc());
                    DocIdSetIterator counting =
                        new DocIdSetIterator() {
                          @Override
                          public int docID() {
                            return it.docID();
                          }

                          @Override
                          public int nextDoc() throws IOException {
                            moves[0]++;
                            return it.nextDoc();
                          }

                          @Override
                          public int advance(int target) throws IOException {
                            moves[0]++;
                            return it.advance(target);
                          }

                          @Override
                          public long cost() {
                            return it.cost();
                          }
                        };
                    return new DefaultScorerSupplier(
                        new ConstantScoreScorer(score(), scoreMode, counting));
                  }

                  @Override
                  public boolean isCacheable(LeafReaderContext ctx) {
                    return false;
                  }
                };
              }

              @Override
              public String toString(String field) {
                return "all";
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
            };
        Query query =
            new BooleanQuery.Builder()
                .add(all, BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term("grp", "old")), BooleanClause.Occur.MUST_NOT)
                .build();
        assertEquals(1_000, searcher.count(query));
        // Expected: the required iterator jumps the excluded run [0, 9000) (about 1,001 moves, as in 10.5.2); actual on main: about 10,001
        assertTrue("required iterator moved " + moves[0] + " times", moves[0] <= 2_000);
      }
    }
  }
}
