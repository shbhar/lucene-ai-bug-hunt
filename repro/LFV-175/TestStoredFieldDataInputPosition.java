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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * StoredFieldDataInput(ByteArrayDataInput) must hold the bytes from the input's current position to
 * its end. ByteArrayDataInput#length() is the end position, so it is only the byte count when the
 * position is 0.
 */
public class TestStoredFieldDataInputPosition extends LuceneTestCase {

  /** {1, 2, 3, ..., size}, so a stored value shows which array indexes it came from. */
  private static byte[] numbered(int size) {
    byte[] b = new byte[size];
    for (int i = 0; i < size; i++) {
      b[i] = (byte) (i + 1);
    }
    return b;
  }

  /** Indexes one document with field "f" from the given input and returns the stored value. */
  private BytesRef storeAndReadBack(StoredFieldDataInput value) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new StoredField("f", value));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        return BytesRef.deepCopyOf(r.storedFields().document(0).getBinaryValue("f"));
      }
    }
  }

  /** Control: a fresh input over a whole array (position 0) works today. */
  public void testWholeArrayControl() throws Exception {
    byte[] buf = numbered(8);
    BytesRef stored = storeAndReadBack(new StoredFieldDataInput(new ByteArrayDataInput(buf)));
    assertEquals(new BytesRef(buf), stored);
  }

  /** An 8-byte slice at offset 4 of a 16-byte array holds 8 bytes. */
  public void testLengthOfSlice() {
    byte[] buf = numbered(16);
    StoredFieldDataInput in = new StoredFieldDataInput(new ByteArrayDataInput(buf, 4, 8));
    assertEquals("length of an 8-byte slice at offset 4", 8, in.getLength());
  }

  /** The stored value of that slice is exactly array indexes 4..11. */
  public void testStoredSliceRoundTrip() throws Exception {
    byte[] buf = numbered(16);
    BytesRef stored = storeAndReadBack(new StoredFieldDataInput(new ByteArrayDataInput(buf, 4, 8)));
    assertEquals(
        "stored value of the slice [4, 12)", new BytesRef(Arrays.copyOfRange(buf, 4, 12)), stored);
  }

  /** An input at offset 0 whose 4-byte header was already read holds the 12 unread bytes. */
  public void testPartlyReadInput() throws Exception {
    byte[] buf = numbered(24);
    ByteArrayDataInput in = new ByteArrayDataInput(buf, 0, 16);
    in.readInt(); // a 4-byte header, read before the rest is stored
    BytesRef stored = storeAndReadBack(new StoredFieldDataInput(in));
    assertEquals(
        "stored value of the unread bytes [4, 16)",
        new BytesRef(Arrays.copyOfRange(buf, 4, 16)),
        stored);
  }

  /** A slice that ends at the end of its array must be indexed, and the writer must stay open. */
  public void testSliceAtEndOfArrayKeepsWriterOpen() throws Exception {
    byte[] buf = numbered(12);
    try (Directory dir = newDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
      try {
        Document earlier = new Document();
        earlier.add(new StoredField("f", new BytesRef(new byte[] {42})));
        w.addDocument(earlier); // buffered, not committed
        Document doc = new Document();
        doc.add(new StoredField("f", new StoredFieldDataInput(new ByteArrayDataInput(buf, 4, 8))));
        try {
          w.addDocument(doc);
        } catch (Throwable t) {
          throw new AssertionError(
              "an 8-byte slice [4, 12) of a 12-byte array must be indexed, got: "
                  + t
                  + "; IndexWriter open: "
                  + w.isOpen()
                  + ", tragic exception: "
                  + w.getTragicException(),
              t);
        }
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          assertEquals(2, r.numDocs());
        }
      } finally {
        w.close();
      }
    }
  }
}
