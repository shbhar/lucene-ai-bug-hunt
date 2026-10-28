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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * StoredFieldDataInput(ByteArrayDataInput) on a ByteArrayDataInput(bytes, offset, len) with a
 * non-zero offset. Public API only. The BytesRef stored field over the same slice is the oracle.
 */
public class TestStoredFieldDataInputOffsetSlice extends LuceneTestCase {

  /** bytes 1, 2, 3, ..., n */
  private static byte[] ramp(int n) {
    byte[] b = new byte[n];
    for (int i = 0; i < n; i++) {
      b[i] = (byte) (i + 1);
    }
    return b;
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
  }

  /**
   * Indexes one document with field "ref" = BytesRef(bytes, offset, len) and field "dataInput" =
   * StoredFieldDataInput(ByteArrayDataInput(bytes, offset, len)), and returns both stored values.
   */
  private BytesRef[] indexAndReadBack(byte[] bytes, int offset, int len) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        Document doc = new Document();
        doc.add(new StoredField("ref", new BytesRef(bytes, offset, len)));
        doc.add(
            new StoredField(
                "dataInput",
                new StoredFieldDataInput(new ByteArrayDataInput(bytes, offset, len))));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Document stored = r.storedFields().document(0);
        return new BytesRef[] {
          BytesRef.deepCopyOf(stored.getBinaryValue("ref")),
          BytesRef.deepCopyOf(stored.getBinaryValue("dataInput"))
        };
      }
    }
  }

  /** Control: offset 0 over the whole array round-trips. */
  public void testControlOffsetZeroWholeArray() throws IOException {
    byte[] bytes = ramp(10);
    assertEquals(10, new StoredFieldDataInput(new ByteArrayDataInput(bytes)).getLength());
    BytesRef[] got = indexAndReadBack(bytes, 0, 10);
    assertEquals(got[0], got[1]);
  }

  /** Control: offset 0, a prefix of the array, round-trips. */
  public void testControlOffsetZeroPrefix() throws IOException {
    byte[] bytes = ramp(10);
    assertEquals(6, new StoredFieldDataInput(new ByteArrayDataInput(bytes, 0, 6)).getLength());
    BytesRef[] got = indexAndReadBack(bytes, 0, 6);
    assertEquals(new BytesRef(bytes, 0, 6), got[0]);
    assertEquals(got[0], got[1]);
  }

  /** The ByteArrayDataInput holds len bytes, so the StoredFieldDataInput length should be len. */
  public void testLengthOfOffsetSlice() {
    byte[] bytes = ramp(20);
    ByteArrayDataInput in = new ByteArrayDataInput(bytes, 5, 7);
    StoredFieldDataInput sfdi = new StoredFieldDataInput(in);
    assertEquals("length of the 7-byte slice [5, 12) of a 20-byte array", 7, sfdi.getLength());
  }

  /** The stored value should be the slice, as for the BytesRef field over the same slice. */
  public void testOffsetSliceStoresTheSlice() throws IOException {
    byte[] bytes = ramp(20);
    BytesRef[] got = indexAndReadBack(bytes, 5, 7);
    assertEquals(new BytesRef(bytes, 5, 7), got[0]); // the oracle field is right
    assertEquals("stored value of the slice [5, 12) of a 20-byte array", got[0], got[1]);
  }

  /** A slice that ends at the end of its array must index without closing the writer. */
  public void testOffsetSliceAtArrayEnd() throws IOException {
    byte[] bytes = ramp(12);
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      Throwable thrown = null;
      try {
        Document doc = new Document();
        doc.add(
            new StoredField(
                "dataInput", new StoredFieldDataInput(new ByteArrayDataInput(bytes, 5, 7))));
        try {
          w.addDocument(doc);
        } catch (Throwable t) {
          thrown = t;
        }
        boolean open = w.isOpen();
        Throwable tragic = w.getTragicException();
        if (thrown != null || open == false || tragic != null) {
          AssertionError e =
              new AssertionError(
                  "addDocument of the 7-byte slice [5, 12) of a 12-byte array: thrown="
                      + thrown
                      + ", writer open="
                      + open
                      + ", tragic exception="
                      + tragic);
          if (thrown != null) {
            e.initCause(thrown);
          }
          throw e;
        }
      } finally {
        if (w.isOpen()) {
          w.close();
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(
            new BytesRef(bytes, 5, 7), r.storedFields().document(0).getBinaryValue("dataInput"));
      }
    }
  }
}
