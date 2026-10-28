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
package org.apache.lucene.spatial.vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * In a non-geo (Cartesian) context, a document without a point must sort after every document that
 * has one, as with the distance sources of BBoxStrategy, SerializedDVStrategy and the prefix-tree
 * strategies (Double.MAX_VALUE).
 */
public class TestPointVectorStrategyMissingDistance extends LuceneTestCase {

  private record Hit(String id, double value) {
    @Override
    public String toString() {
      return id + "=" + value;
    }
  }

  private SpatialContext ctx;
  private PointVectorStrategy strategy;
  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    factory.worldBounds = new RectangleImpl(-1000, 1000, -1000, 1000, null);
    ctx = factory.newSpatialContext();
    strategy = PointVectorStrategy.newInstance(ctx, "pv");
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      addDoc(w, "near", ctx.getShapeFactory().pointXY(100, 0));
      addDoc(w, "far", ctx.getShapeFactory().pointXY(500, 0));
      addDoc(w, "none", null);
    }
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private void addDoc(IndexWriter w, String id, Point point) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    if (point != null) {
      for (Field f : strategy.createIndexableFields(point)) {
        doc.add(f);
      }
    }
    w.addDocument(doc);
  }

  /** All documents, sorted by the source, with their sort values. */
  private List<Hit> sortBy(DoubleValuesSource source, boolean reverse) throws IOException {
    Sort sort = new Sort(source.getSortField(reverse));
    TopFieldDocs top = searcher.search(new MatchAllDocsQuery(), 10, sort);
    List<Hit> hits = new ArrayList<>();
    for (ScoreDoc sd : top.scoreDocs) {
      String id = searcher.storedFields().document(sd.doc).get("id");
      hits.add(new Hit(id, (Double) ((FieldDoc) sd).fields[0]));
    }
    return hits;
  }

  private static List<String> ids(List<Hit> hits) {
    return hits.stream().map(Hit::id).toList();
  }

  public void testAscendingDistanceSortPutsMissingLast() throws IOException {
    Point origin = ctx.getShapeFactory().pointXY(0, 0);
    List<Hit> hits = sortBy(strategy.makeDistanceValueSource(origin), false);
    assertEquals(hits.toString(), List.of("near", "far", "none"), ids(hits));
    assertEquals(hits.toString(), Double.MAX_VALUE, hits.get(2).value(), 0.0);
  }

  public void testRecipDistanceScoreOfMissingIsLowest() throws IOException {
    // query box -1000..1000, center (0, 0): near scores 0.586 and far (500 units away) 0.220
    Rectangle box = ctx.getShapeFactory().rect(-1000, 1000, -1000, 1000);
    List<Hit> hits = sortBy(strategy.makeRecipDistanceValueSource(box), true);
    assertEquals(hits.toString(), List.of("near", "far", "none"), ids(hits));
  }
}
