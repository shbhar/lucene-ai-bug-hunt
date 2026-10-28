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

import org.apache.lucene.geo.Component2D;
import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PointValues.Relation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Shape doc values read with the bulk BinaryDocValues#binaryValues API must relate like the same
 * values read one at a time. Uses only public API.
 */
public class TestShapeDocValuesBulkBinaryValues extends LuceneTestCase {

  /** 10 x 10 degree square, tessellated into two triangles */
  private static Polygon square() {
    return new Polygon(new double[] {0, 0, 10, 10, 0}, new double[] {0, 10, 10, 0, 0});
  }

  /**
   * The default codec's bulk BinaryDocValues#binaryValues returns slices of one shared array for
   * contiguous documents, so every value after the first has offset != 0. Doc 0 is a square far
   * away from the query box, docs 1 and 2 are the square that the query box overlaps.
   */
  public void testBulkBinaryValuesDefaultCodec() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Polygon far = new Polygon(new double[] {40, 40, 50, 50, 40}, new double[] {40, 50, 50, 40, 40});
        for (Polygon p : new Polygon[] {far, square(), square()}) {
          Document doc = new Document();
          doc.add(LatLonShape.createDocValueField("shape", p));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        LeafReader leaf = reader.leaves().get(0).reader();
        BytesRef[] values = new BytesRef[3];
        leaf.getBinaryDocValues("shape").binaryValues(3, new int[] {0, 1, 2}, values);

        Component2D box = LatLonGeometry.create(new Rectangle(5, 15, 5, 15));
        BinaryDocValues single = leaf.getBinaryDocValues("shape");
        for (int doc = 0; doc < 3; doc++) {
          assertTrue(single.advanceExact(doc));
          Relation expected =
              LatLonShape.createLatLonShapeDocValues(BytesRef.deepCopyOf(single.binaryValue()))
                  .relate(box);
          assertEquals(
              doc == 0 ? Relation.CELL_OUTSIDE_QUERY : Relation.CELL_CROSSES_QUERY, expected);
          assertEquals(
              "doc " + doc + " (bulk value offset " + values[doc].offset + ")",
              expected,
              LatLonShape.createLatLonShapeDocValues(values[doc]).relate(box));
        }
      }
    }
  }
}
