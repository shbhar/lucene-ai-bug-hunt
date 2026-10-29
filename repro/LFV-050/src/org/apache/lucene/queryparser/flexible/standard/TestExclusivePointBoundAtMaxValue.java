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
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestExclusivePointBoundAtMaxValue extends LuceneTestCase {

  public void testNothingIsGreaterThanMaxValue() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new IntPoint("i", 0));
        w.addDocument(doc);
      }
      StandardQueryParser qp = new StandardQueryParser();
      NumberFormat format = NumberFormat.getIntegerInstance(Locale.ROOT);
      qp.setPointsConfigMap(Map.of("i", new PointsConfig(format, Integer.class)));
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // expected 0 hits (no int is > Integer.MAX_VALUE); actual 1, because MAX_VALUE + 1 wraps
        assertEquals(0, new IndexSearcher(r).count(qp.parse("i:{2147483647 TO *]", "i")));
      }
    }
  }
}
