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
package verify.v1;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.LeafFieldComparator;
import org.apache.lucene.search.Pruning;
import org.apache.lucene.search.SortField;
import org.apache.lucene.spatial3d.Geo3DDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Comparator-level round trip through public API (SortField#getComparator): LeafFieldComparator
 * javadoc says compareTop "should return the same result as compare(int,int) as if topValue were
 * slot1 and the new document were slot 2". So setTopValue(value(slot)) for the doc copied into slot
 * must give compareTop(sameDoc) == 0.
 */
public class TestGeo3DComparatorTopRoundTrip extends LuceneTestCase {

  private void roundTrip(PlanetModel pm, SortField sf) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d = new Document();
        d.add(
            new Geo3DDocValuesField(
                "loc", new GeoPoint(pm, Math.toRadians(4.0), Math.toRadians(2.0)), pm));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReaderContext ctx = r.leaves().get(0);
        @SuppressWarnings("unchecked")
        FieldComparator<Double> c1 = (FieldComparator<Double>) sf.getComparator(1, Pruning.NONE);
        LeafFieldComparator l1 = c1.getLeafComparator(ctx);
        l1.copy(0, 0);
        Double v = c1.value(0);

        @SuppressWarnings("unchecked")
        FieldComparator<Double> c2 = (FieldComparator<Double>) sf.getComparator(1, Pruning.NONE);
        c2.setTopValue(v);
        LeafFieldComparator l2 = c2.getLeafComparator(ctx);
        int cmp = l2.compareTop(0);
        assertEquals(sf + ": value(slot)=" + v + " fed back via setTopValue; compareTop(sameDoc)", 0, cmp);
      }
    }
  }

  public void testDistanceWgs84() throws Exception {
    roundTrip(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newDistanceSort("loc", 0.0, 0.0, 5_000_000.0, PlanetModel.WGS84));
  }

  public void testOutsideDistanceWgs84() throws Exception {
    roundTrip(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newOutsideDistanceSort("loc", 0.0, 0.0, 10_000.0, PlanetModel.WGS84));
  }

  public void testDistanceSphereControl() throws Exception {
    roundTrip(
        PlanetModel.SPHERE,
        Geo3DDocValuesField.newDistanceSort("loc", 0.0, 0.0, 1.0, PlanetModel.SPHERE));
  }
}
