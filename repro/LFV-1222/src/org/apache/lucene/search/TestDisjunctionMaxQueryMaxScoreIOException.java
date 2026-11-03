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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDisjunctionMaxQueryMaxScoreIOException extends LuceneTestCase {

  public void testGetMaxScoreIOExceptionIsPropagated() throws Exception {
    Query failing =
        new Query() {
          @Override
          public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
            return new ConstantScoreWeight(this, boost) {
              @Override
              public ScorerSupplier scorerSupplier(LeafReaderContext context) {
                DocIdSetIterator it = DocIdSetIterator.all(context.reader().maxDoc());
                Scorer scorer =
                    new Scorer() {
                      @Override
                      public int docID() {
                        return it.docID();
                      }

                      @Override
                      public DocIdSetIterator iterator() {
                        return it;
                      }

                      @Override
                      public float score() {
                        return boost;
                      }

                      @Override
                      public float getMaxScore(int upTo) throws IOException {
                        throw new IOException("getMaxScore failed");
                      }
                    };
                return new ScorerSupplier() {
                  @Override
                  public Scorer get(long leadCost) {
                    return scorer;
                  }

                  @Override
                  public long cost() {
                    return it.cost();
                  }
                };
              }

              @Override
              public boolean isCacheable(LeafReaderContext ctx) {
                return false;
              }
            };
          }

          @Override
          public String toString(String field) {
            return "failing";
          }

          @Override
          public void visit(QueryVisitor visitor) {}

          @Override
          public boolean equals(Object other) {
            return this == other;
          }

          @Override
          public int hashCode() {
            return System.identityHashCode(this);
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q = new DisjunctionMaxQuery(List.of(new MatchAllDocsQuery(), failing), 0.1f);
        // expected: the IOException from getMaxScore; actual: RuntimeException wrapping it
        expectThrows(IOException.class, () -> new IndexSearcher(reader).search(q, 10));
      }
    }
  }
}
