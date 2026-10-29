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
package org.apache.lucene.queryparser.flexible.standard;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPointRangeExclusiveZeroBound extends LuceneTestCase {

  public void testLessThanZeroMatchesNegativeZero() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new FloatPoint("f", -0f));
        w.addDocument(doc);
      }
      StandardQueryParser parser = new StandardQueryParser();
      parser.setPointsConfigMap(
          Map.of("f", new PointsConfig(NumberFormat.getInstance(Locale.ROOT), Float.class)));
      Query query = parser.parse("f:[* TO 0}", "f");
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // expected: -0f < 0f in FloatPoint's Float.compare order, so 1 hit; actual: 0 hits
        assertEquals("hits of " + query, 1, new IndexSearcher(reader).count(query));
      }
    }
  }
}
