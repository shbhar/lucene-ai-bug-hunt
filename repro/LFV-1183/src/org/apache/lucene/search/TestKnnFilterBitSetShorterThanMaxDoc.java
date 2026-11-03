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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.FixedBitSet;

public class TestKnnFilterBitSetShorterThanMaxDoc extends LuceneTestCase {

  private static final class FirstTenDocsQuery extends Query {
    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
      return new ConstantScoreWeight(this, boost) {
        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) {
          FixedBitSet bits = new FixedBitSet(10);
          bits.set(0, 10);
          return new DefaultScorerSupplier(
              new ConstantScoreScorer(score(), scoreMode, new BitSetIterator(bits, 10)));
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
          return false;
        }
      };
    }

    @Override
    public String toString(String field) {
      return "FirstTenDocsQuery";
    }

    @Override
    public void visit(QueryVisitor visitor) {}

    @Override
    public boolean equals(Object other) {
      return sameClassAs(other);
    }

    @Override
    public int hashCode() {
      return classHash();
    }
  }

  public void testFilterBitSetShorterThanMaxDoc() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 100; i++) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {i}));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query query = new KnnFloatVectorQuery("v", new float[] {50}, 1, new FirstTenDocsQuery());
        // expected: doc 9 (nearest filtered doc); actual: AIOOBE (-da) / AssertionError (-ea) in FixedBitSet.get
        assertEquals(9, new IndexSearcher(reader).search(query, 1).scoreDocs[0].doc);
      }
    }
  }
}
