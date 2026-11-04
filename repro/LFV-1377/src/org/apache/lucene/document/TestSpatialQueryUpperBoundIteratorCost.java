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
package org.apache.lucene.document;

import java.io.IOException;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.PointValues.IntersectVisitor;
import org.apache.lucene.index.PointValues.PointTree;
import org.apache.lucene.index.PointValues.Relation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.FixedBitSet;

public class TestSpatialQueryUpperBoundIteratorCost extends LuceneTestCase {

  private static PointTree wrap(PointTree in, int maxDoc) {
    return new PointTree() {
      @Override
      public PointTree clone() {
        return wrap(in.clone(), maxDoc);
      }

      @Override
      public boolean moveToChild() throws IOException {
        return in.moveToChild();
      }

      @Override
      public boolean moveToSibling() throws IOException {
        return in.moveToSibling();
      }

      @Override
      public boolean moveToParent() throws IOException {
        return in.moveToParent();
      }

      @Override
      public byte[] getMinPackedValue() {
        return in.getMinPackedValue();
      }

      @Override
      public byte[] getMaxPackedValue() {
        return in.getMaxPackedValue();
      }

      @Override
      public long size() {
        return in.size();
      }

      @Override
      public void visitDocIDs(IntersectVisitor visitor) throws IOException {
        in.visitDocIDs(visitor);
      }

      @Override
      public void visitDocValues(IntersectVisitor visitor) throws IOException {
        in.visitDocValues(
            new IntersectVisitor() {
              @Override
              public void visit(int docID) throws IOException {
                visitor.visit(docID);
              }

              @Override
              public void visit(int docID, byte[] packedValue) throws IOException {
                FixedBitSet bits = new FixedBitSet(maxDoc);
                bits.set(docID);
                visitor.visit(new BitSetIterator(bits, maxDoc), packedValue);
              }

              @Override
              public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
                return visitor.compare(minPackedValue, maxPackedValue);
              }
            });
      }
    };
  }

  public void testWithinWithUpperBoundBulkCost() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document inside = new Document();
      inside.add(new LatLonPoint("p", 1, 1));
      w.addDocument(inside);
      Document outside = new Document();
      outside.add(new LatLonPoint("p", 50, 50));
      outside.add(new LatLonPoint("p", 60, 60));
      w.addDocument(outside);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        LeafReader leaf =
            new FilterLeafReader(getOnlyLeafReader(r)) {
              @Override
              public PointValues getPointValues(String field) {
                PointValues in = super.getPointValues(field);
                int maxDoc = maxDoc();
                return new PointValues() {
                  @Override
                  public PointTree getPointTree() throws IOException {
                    return wrap(in.getPointTree(), maxDoc);
                  }

                  @Override
                  public byte[] getMinPackedValue() {
                    return in.getMinPackedValue();
                  }

                  @Override
                  public byte[] getMaxPackedValue() {
                    return in.getMaxPackedValue();
                  }

                  @Override
                  public int getNumDimensions() {
                    return in.getNumDimensions();
                  }

                  @Override
                  public int getNumIndexDimensions() {
                    return in.getNumIndexDimensions();
                  }

                  @Override
                  public int getBytesPerDimension() {
                    return in.getBytesPerDimension();
                  }

                  @Override
                  public long size() {
                    return in.size();
                  }

                  @Override
                  public int getDocCount() {
                    return in.getDocCount();
                  }
                };
              }

              @Override
              public CacheHelper getCoreCacheHelper() {
                return null;
              }

              @Override
              public CacheHelper getReaderCacheHelper() {
                return null;
              }
            };
        Polygon square = new Polygon(new double[] {0, 0, 10, 10, 0}, new double[] {0, 10, 10, 0, 0});
        // expected 1 hit (doc 0 is inside); actual 0: cost() = maxDoc, an allowed upper bound, empties the counter
        assertEquals(
            1,
            new IndexSearcher(leaf)
                .count(LatLonPoint.newGeometryQuery("p", ShapeField.QueryRelation.WITHIN, square)));
      }
    }
  }
}
