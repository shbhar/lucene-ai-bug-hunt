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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.FixedBitSet;

public class TestReversedRangeIntoBitSet extends LuceneTestCase {

  public void testReversedRangeSetsNoBits() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (int i = 0; i < 16; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("f", i));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        NumericDocValues dv = getOnlyLeafReader(reader).getNumericDocValues("f");
        FixedBitSet bits = new FixedBitSet(16);
        dv.rangeIntoBitSet(12, 5, Long.MIN_VALUE, Long.MAX_VALUE, bits, 0);
        // Expected: [12, 5) is empty, so no bit is set; actual with the Panama provider: doc 4 is set.
        assertEquals(0, bits.cardinality());
      }
    }
  }
}
