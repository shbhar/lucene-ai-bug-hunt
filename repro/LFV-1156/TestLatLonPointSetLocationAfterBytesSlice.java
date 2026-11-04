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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestLatLonPointSetLocationAfterBytesSlice extends LuceneTestCase {

  public void testSetLocationValueAfterSlicedBytesValue() throws Exception {
    LatLonPoint field = new LatLonPoint("p", 10, 20);
    field.setBytesValue(new BytesRef(new byte[16], 8, 8)); // accepted (IntPoint would throw)
    field.setLocationValue(-30, -40);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(field);
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        // setLocationValue wrote bytes 0-7 of the array; indexing reads the slice, bytes 8-15
        assertEquals(1, s.count(LatLonPoint.newBoxQuery("p", -31, -29, -41, -39)));
      }
    }
  }
}
