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
package org.apache.lucene.spatial.util;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.spatial.ShapeValues;
import org.apache.lucene.spatial.ShapeValuesSource;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Shape;

/**
 * ShapeAreaValueSource multiplies the area by its multiplier but leaves the multiplier out of
 * equals/hashCode. Value sources (and the FunctionScoreQuery objects built on them) that differ
 * only in the multiplier are therefore equal, and BooleanQuery merges them as duplicate clauses.
 */
public class TestShapeAreaValueSourceEquals extends LuceneTestCase {

  /** Every document has the same 10 x 10 rectangle (non-geo area 100). */
  private static final ShapeValuesSource SHAPES =
      new ShapeValuesSource() {
        final Shape rect = SpatialContext.GEO.getShapeFactory().rect(0, 10, 0, 10);

        @Override
        public ShapeValues getValues(LeafReaderContext ctx) {
          return new ShapeValues() {
            @Override
            public boolean advanceExact(int doc) {
              return true;
            }

            @Override
            public Shape value() {
              return rect;
            }
          };
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
          return true;
        }
      };

  public void testDifferentMultipliersAreNotEqual() {
    ShapeAreaValueSource times1 = new ShapeAreaValueSource(SHAPES, SpatialContext.GEO, false, 1.0);
    ShapeAreaValueSource times2 = new ShapeAreaValueSource(SHAPES, SpatialContext.GEO, false, 2.0);
    assertFalse("sources with different multipliers must not be equal", times1.equals(times2));
  }

  public void testDisjunctionScoresAsSum() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query q1 =
            new FunctionScoreQuery(
                new TermQuery(new Term("id", "1")), new ShapeAreaValueSource(SHAPES, SpatialContext.GEO, false, 1.0));
        Query q2 =
            new FunctionScoreQuery(
                new TermQuery(new Term("id", "1")), new ShapeAreaValueSource(SHAPES, SpatialContext.GEO, false, 2.0));
        float s1 = searcher.search(q1, 1).scoreDocs[0].score;
        float s2 = searcher.search(q2, 1).scoreDocs[0].score;
        assertTrue("precondition: the multipliers give different scores", s1 != s2);
        Query both = new BooleanQuery.Builder().add(q1, Occur.SHOULD).add(q2, Occur.SHOULD).build();
        float s = searcher.search(both, 1).scoreDocs[0].score;
        assertEquals(
            "q1 OR q2 must score s1 + s2 (s1=" + s1 + ", s2=" + s2 + "); rewritten to " + searcher.rewrite(both),
            s1 + s2,
            s,
            1e-3f);
      }
    }
  }
}
