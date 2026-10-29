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

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDistanceQueryNearPoleNaN extends LuceneTestCase {

  public void testLegalCircleEndingJustShortOfPole() throws Exception {
    double lat = 0.43945312499999994;
    double radiusMeters = 9958692.080819247;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new LatLonPoint("p", lat, 0));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        Query q = LatLonPoint.newDistanceQuery("p", lat, 0, radiusMeters);
        try {
          // expected: the centre point matches (1 hit); actual: IllegalArgumentException
          assertEquals(1, new IndexSearcher(r).count(q));
        } catch (IllegalArgumentException e) {
          throw new AssertionError("expected 1 hit for a legal circle, got " + e, e);
        }
      }
    }
  }
}
