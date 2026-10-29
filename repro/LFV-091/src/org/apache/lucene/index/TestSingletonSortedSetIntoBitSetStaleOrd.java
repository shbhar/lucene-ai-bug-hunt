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
package org.apache.lucene.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

public class TestSingletonSortedSetIntoBitSetStaleOrd extends LuceneTestCase {

  public void testNextOrdAfterIntoBitSet() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String value : new String[] {"a", "b"}) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesField("f", new BytesRef(value)));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SortedSetDocValues values = DocValues.getSortedSet(reader.leaves().get(0).reader(), "f");
        values.nextDoc();
        values.intoBitSet(1, new FixedBitSet(1), 0);
        // now on doc 1 ("b", ord 1): nextOrd() should return 1 but returns doc 0's ord 0 ("a")
        assertEquals("nextOrd() on doc " + values.docID(), 1, values.nextOrd());
      }
    }
  }
}
