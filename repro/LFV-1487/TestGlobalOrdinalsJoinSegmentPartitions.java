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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FilterScorer;
import org.apache.lucene.search.FilterWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NamedThreadFactory;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestGlobalOrdinalsJoinSegmentPartitions extends LuceneTestCase {

  // Expected: the parent's score is the total of its two children's scores, 2.0. Actual: 1.0.
  public void testTotalScoreOfOneSegmentSearchedInPartitions() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("join"));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc("child"));
      w.addDocument(doc("child"));
      w.addDocument(doc("parent"));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher =
            new IndexSearcher(reader, executor) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return slices(leaves, 1, 1, true);
              }
            };
        Query join =
            JoinUtil.createJoinQuery(
                "join",
                new ChildrenScoringOne(),
                new TermQuery(new Term("type", "parent")),
                searcher,
                ScoreMode.Total,
                null);
        assertEquals(2f, searcher.search(join, 1).scoreDocs[0].score, 0f);
      }
    } finally {
      TestUtil.shutdownExecutorService(executor);
    }
  }

  private static Document doc(String type) {
    Document doc = new Document();
    doc.add(new StringField("type", type, Field.Store.NO));
    doc.add(new SortedDocValuesField("join", new BytesRef("k")));
    return doc;
  }

  private static final class ChildrenScoringOne extends Query {
    private final CountDownLatch bothChildrenAreBeingScored = new CountDownLatch(2);

    @Override
    public Weight createWeight(
        IndexSearcher searcher, org.apache.lucene.search.ScoreMode scoreMode, float boost)
        throws IOException {
      Weight children =
          new TermQuery(new Term("type", "child"))
              .createWeight(searcher, org.apache.lucene.search.ScoreMode.COMPLETE_NO_SCORES, 1f);
      return new FilterWeight(this, children) {
        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
          return new DefaultScorerSupplier(
              new FilterScorer(children.scorer(context)) {
                @Override
                public float score() {
                  bothChildrenAreBeingScored.countDown();
                  try {
                    bothChildrenAreBeingScored.await(5, TimeUnit.SECONDS);
                  } catch (InterruptedException e) {
                    throw new ThreadInterruptedException(e);
                  }
                  return 1f;
                }

                @Override
                public float getMaxScore(int upTo) {
                  return 1f;
                }
              });
        }
      };
    }

    @Override
    public void visit(QueryVisitor visitor) {
      visitor.visitLeaf(this);
    }

    @Override
    public String toString(String field) {
      return "children";
    }

    @Override
    public boolean equals(Object other) {
      return other == this;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}
