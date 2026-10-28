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
package org.apache.lucene.spatial.bbox;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Metamorphic oracle: in a geo context, rotating both the query box and the document box by the
 * same longitude offset does not change their overlap, so the overlap-ratio score must not change.
 * No hand-computed expected value is used.
 */
public class TestOverlapRatioRotationAudit extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  private static double norm(double lon) {
    double x = lon;
    while (x > 180) x -= 360;
    while (x < -180) x += 360;
    return x;
  }

  private static Rectangle rot(double minX, double maxX, double minY, double maxY, double d) {
    return CTX.getShapeFactory().rect(norm(minX + d), norm(maxX + d), minY, maxY);
  }

  private static double score(Rectangle query, Rectangle doc) throws IOException {
    BBoxStrategy strategy = BBoxStrategy.newInstance(CTX, "bbox");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document d = new Document();
        for (Field f : strategy.createIndexableFields(doc)) d.add(f);
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        DoubleValuesSource vs = strategy.makeOverlapRatioValueSource(query, 0.5);
        DoubleValues v = vs.getValues(r.leaves().get(0), null);
        assertTrue(v.advanceExact(0));
        return v.doubleValue();
      }
    }
  }

  /** Control: a pair that never needs both boxes to cross; scores must be rotation invariant. */
  public void testControlOneCrossingRotations() throws IOException {
    double ref = score(rot(-60, 60, -10, 10, 0), rot(30, 100, -10, 10, 0));
    for (double d : new double[] {130, 150, 170, -170}) {
      Rectangle q = rot(-60, 60, -10, 10, d);
      Rectangle t = rot(30, 100, -10, 10, d);
      assertEquals("rot " + d + " q=" + q + " t=" + t, ref, score(q, t), 1e-9);
    }
  }

  /**
   * Query covers 180 degrees and the document 210 degrees, both wrapping. Offset -30 makes only the
   * document cross the dateline; offset 0 makes both cross.
   */
  public void testWideWrappingPairRotation() throws IOException {
    Rectangle qOne = rot(10, -170, -10, 10, -30); // [-20,160], no crossing
    Rectangle tOne = rot(170, 20, -10, 10, -30); // [140,-10], crosses
    assertFalse(qOne.getCrossesDateLine());
    assertTrue(tOne.getCrossesDateLine());
    Rectangle qBoth = rot(10, -170, -10, 10, 0);
    Rectangle tBoth = rot(170, 20, -10, 10, 0);
    assertTrue(qBoth.getCrossesDateLine());
    assertTrue(tBoth.getCrossesDateLine());
    double one = score(qOne, tOne);
    double both = score(qBoth, tBoth);
    assertEquals("one-crossing=" + one + " both-crossing=" + both, one, both, 1e-9);
  }
}
