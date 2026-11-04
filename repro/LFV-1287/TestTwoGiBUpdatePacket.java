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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.RamUsageEstimator;

public class TestTwoGiBUpdatePacket extends LuceneTestCase {

  public void testWriterCleanAfterCommit() throws Exception {
    assumeTrue(
        "sizes assume compressed oops and 12-byte headers",
        RamUsageEstimator.COMPRESSED_REFS_ENABLED
            && RamUsageEstimator.NUM_BYTES_OBJECT_HEADER == 12);
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMaxBufferedDocs(1000)
            .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StringField("primary_key", "0", Field.Store.NO));
      doc.add(new NumericDocValuesField("dv", 0));
      w.addDocument(doc);
      w.commit();
      w.updateNumericDocValue(new Term("primary_key", "0"), "dv", 1);
      String longField = "f".repeat(56_511_839);
      for (int i = 0; i < 19; i++) {
        w.updateNumericDocValue(new Term(longField, "t"), "dv", 2);
      }
      w.commit();
      // expected: nothing pending after commit; actual: true forever (estimate exactly 2^31)
      assertFalse(w.hasUncommittedChanges());
    }
  }
}
