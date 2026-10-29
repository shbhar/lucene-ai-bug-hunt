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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDoubleRangeSignedZeroBounds extends LuceneTestCase {

  public void testAcceptedZeroToNegativeZeroIntersectsZero() throws IOException {
    DoubleRange field;
    try {
      field = new DoubleRange("r", new double[] {0.0}, new double[] {-0.0});
    } catch (IllegalArgumentException rejected) {
      return;
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(field);
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        double[] zero = {0.0};
        // expected 1 (or rejection above); actual 0: [0.0, -0.0] is accepted but stored inverted
        assertEquals(
            "INTERSECTS [0.0, 0.0] hits for an indexed DoubleRange [0.0, -0.0]",
            1,
            new IndexSearcher(reader).count(DoubleRange.newIntersectsQuery("r", zero, zero)));
      }
    }
  }
}
