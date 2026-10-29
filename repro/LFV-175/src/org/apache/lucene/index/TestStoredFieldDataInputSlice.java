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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestStoredFieldDataInputSlice extends LuceneTestCase {

  public void testStoredValueOfSliceAtNonZeroOffset() throws IOException {
    byte[] buf = new byte[16];
    for (int i = 0; i < buf.length; i++) {
      buf[i] = (byte) i;
    }
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new StoredField("f", new StoredFieldDataInput(new ByteArrayDataInput(buf, 4, 8))));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // expected: the 8 bytes buf[4..12); actual: the 12 bytes buf[4..16)
        assertEquals(new BytesRef(buf, 4, 8), r.storedFields().document(0).getBinaryValue("f"));
      }
    }
  }
}
