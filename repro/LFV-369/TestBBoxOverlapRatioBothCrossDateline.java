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
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Two boxes that both cross the dateline can overlap twice: once across the dateline and once on
 * the far side of the globe. BBoxStrategy's overlap-ratio score counts only the first piece.
 *
 * <p>Query [10, 180] + [-180, -170] (180 degrees wide) and document [170, 180] + [-180, 20] (210
 * degrees wide), both 20 degrees tall: they share 170..-170 across the dateline (20 degrees) and
 * 10..20 (10 degrees), so the intersection is 30 x 20.
 */
public class TestBBoxOverlapRatioBothCrossDateline extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;
  private static final double P = 0.5; // queryTargetProportion

  private static Rectangle rect(double minX, double maxX, double minY, double maxY) {
    return CTX.getShapeFactory().rect(minX, maxX, minY, maxY);
  }

  /**
   * The documented formula: intersectionArea / queryArea * p + intersectionArea / docArea * (1-p).
   */
  private static double documented(double intersectionArea, double queryArea, double docArea) {
    return intersectionArea / queryArea * P + intersectionArea / docArea * (1 - P);
  }

  /** Indexes one document with BBoxStrategy defaults and returns its overlap-ratio value. */
  private static double overlapRatio(Rectangle query, Rectangle doc) throws IOException {
    BBoxStrategy strategy = BBoxStrategy.newInstance(CTX, "bbox");
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        Document d = new Document();
        for (Field f : strategy.createIndexableFields(doc)) {
          d.add(f);
        }
        writer.addDocument(d);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DoubleValuesSource overlap = strategy.makeOverlapRatioValueSource(query, P);
        DoubleValues values = overlap.getValues(reader.leaves().get(0), null);
        assertTrue(values.advanceExact(0));
        return values.doubleValue();
      }
    }
  }

  /** Fails: the score is 0.10317460317460317 (width 20) instead of 0.15476190476190477 (30). */
  public void testBothCrossDatelineTwoPieceOverlap() throws IOException {
    Rectangle query = rect(10, -170, -10, 10);
    Rectangle doc = rect(170, 20, -10, 10);
    assertTrue(query.getCrossesDateLine() && doc.getCrossesDateLine());
    double expected = documented(30 * 20, 180 * 20, 210 * 20); // 0.15476190476190477
    assertEquals(expected, overlapRatio(query, doc), 1e-9);
  }

  /**
   * Control, passes: the same two boxes moved 30 degrees west, so only the document crosses the
   * dateline. Moving both boxes by the same longitude does not change their overlap, and this
   * branch of the code adds up both pieces: 0.15476190476190477.
   */
  public void testSamePairMovedWestSoOnlyTheDocumentCrosses() throws IOException {
    Rectangle query = rect(-20, 160, -10, 10); // [-20, 160]
    Rectangle doc = rect(140, -10, -10, 10); // [140, 180] + [-180, -10]
    assertFalse(query.getCrossesDateLine());
    assertTrue(doc.getCrossesDateLine());
    double expected = documented(30 * 20, 180 * 20, 210 * 20);
    assertEquals(expected, overlapRatio(query, doc), 1e-9);
  }

  /**
   * Fails: sorting by the overlap ratio puts the worse match first. Document A is the box above
   * (true score 0.1548, computed 0.1032). Document B, [-145, 35], overlaps the query only in 10..35
   * and does not cross the dateline, so its score 0.1389 is computed correctly.
   */
  public void testSortByOverlapRatioRanksTheBetterMatchSecond() throws IOException {
    BBoxStrategy strategy = BBoxStrategy.newInstance(CTX, "bbox");
    Rectangle query = rect(10, -170, -10, 10);
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        for (String id : new String[] {"A", "B"}) {
          Rectangle box = id.equals("A") ? rect(170, 20, -10, 10) : rect(-145, 35, -10, 10);
          Document d = new Document();
          d.add(new StoredField("id", id));
          for (Field f : strategy.createIndexableFields(box)) {
            d.add(f);
          }
          writer.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        DoubleValuesSource overlap = strategy.makeOverlapRatioValueSource(query, P);
        TopFieldDocs top =
            searcher.search(new MatchAllDocsQuery(), 2, new Sort(overlap.getSortField(true)));
        StringBuilder ranking = new StringBuilder();
        for (int i = 0; i < top.scoreDocs.length; i++) {
          FieldDoc hit = (FieldDoc) top.scoreDocs[i];
          String id = searcher.storedFields().document(hit.doc).get("id");
          ranking.append(i + 1).append(". ").append(id).append('=').append(hit.fields[0]);
          ranking.append(' ');
        }
        String first = searcher.storedFields().document(top.scoreDocs[0].doc).get("id");
        assertEquals("A has the larger overlap; ranking: " + ranking, "A", first);
      }
    }
  }
}
