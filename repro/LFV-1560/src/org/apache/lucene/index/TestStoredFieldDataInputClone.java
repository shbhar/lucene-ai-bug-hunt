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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestStoredFieldDataInputClone extends LuceneTestCase {

  public void testPeekThroughCloneThenReadValue() throws IOException {
    byte[] payload = new byte[200_000];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) i;
    }
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new StoredField("payload", payload));
      doc.add(new StoredField("id", "1"));
      writer.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        byte[][] read = new byte[1][];
        reader
            .storedFields()
            .document(
                0,
                new StoredFieldVisitor() {
                  @Override
                  public Status needsField(FieldInfo fieldInfo) {
                    return read[0] == null ? Status.YES : Status.STOP;
                  }

                  @Override
                  public void binaryField(FieldInfo fieldInfo, StoredFieldDataInput value)
                      throws IOException {
                    DataInput in = value.getDataInput();
                    in.clone().readByte();
                    read[0] = new byte[value.getLength()];
                    in.readBytes(read[0], 0, read[0].length);
                  }
                });
        // expected: the stored bytes; actual: shifted by one byte (the clone moved the input too)
        assertArrayEquals(payload, read[0]);
      }
    }
  }
}
