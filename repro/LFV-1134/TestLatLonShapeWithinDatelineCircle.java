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

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Circle;
import org.apache.lucene.geo.Line;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLatLonShapeWithinDatelineCircle extends LuceneTestCase {

  public void testLineThroughLon0IsNotWithinDatelineCircle() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field f :
          LatLonShape.createIndexableFields(
              "shape", new Line(new double[] {0, 0}, new double[] {-179.5, 179.5}))) {
        doc.add(f);
      }
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        // expected 0: the indexed edge runs straight through lon 0, far outside the circle
        assertEquals(
            0,
            new IndexSearcher(reader)
                .count(
                    LatLonShape.newDistanceQuery(
                        "shape", QueryRelation.WITHIN, new Circle(0, 180, 100000))));
      }
    }
  }
}
