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
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTryUpdateDocValueRamBuffer extends LuceneTestCase {

  public void testTryUpdateDocValueRespectsRamBuffer() throws Exception {
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter writer =
            new IndexWriter(dir, new IndexWriterConfig().setRAMBufferSizeMB(1))) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("counter", 0));
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        SegmentCommitInfo info =
            ((SegmentReader) reader.leaves().get(0).reader()).getOriginalSegmentInfo();
        for (int i = 1; i <= 10_000; i++) {
          writer.tryUpdateDocValue(reader, 0, new NumericDocValuesField("counter", i));
        }
        long pending = writer.getPooledInstance(info, false).ramBytesUsed.get();
        // Expected: pending updates stay within the 1 MB RAM buffer; actual: 3,340,000 bytes.
        assertTrue("pending doc-values updates use " + pending + " bytes", pending <= 1024 * 1024);
      }
    }
  }
}
