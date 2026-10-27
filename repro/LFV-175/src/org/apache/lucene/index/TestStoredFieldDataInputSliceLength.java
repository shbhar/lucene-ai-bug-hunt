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
 * StoredFieldDataInput(ByteArrayDataInput) takes its length from ByteArrayDataInput#length(), which
 * returns the end position (offset + len) of the input, not the number of bytes it holds. A
 * ByteArrayDataInput over a slice that does not start at 0 therefore gets a length that is too
 * large by the slice offset.
 */
public class TestStoredFieldDataInputSliceLength extends LuceneTestCase {

  private static byte[] buffer(int size) {
    byte[] b = new byte[size];
    for (int i = 0; i < size; i++) {
      b[i] = (byte) (i + 1);
    }
    return b;
  }

  /** The constructor itself: a slice of 8 bytes should have length 8. */
  public void testLengthOfSlice() {
    byte[] buf = buffer(16);
    StoredFieldDataInput in = new StoredFieldDataInput(new ByteArrayDataInput(buf, 4, 8));
    assertEquals("length of an 8-byte slice", 8, in.getLength());
  }

  /** The stored value of a slice in the middle of a buffer must be exactly the slice. */
  public void testStoredSliceRoundTrip() throws Exception {
    byte[] buf = buffer(16);
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new StoredField("f", new StoredFieldDataInput(new ByteArrayDataInput(buf, 4, 8))));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        BytesRef stored = r.storedFields().document(0).getBinaryValue("f");
        assertEquals(
            "stored bytes of the slice [4, 12)",
            new BytesRef(Arrays.copyOfRange(buf, 4, 12)),
            stored);
      }
    }
  }

  /**
   * A slice that ends at the end of its buffer: the writer reads past the array, and the exception
   * it hits inside the stored fields writer is an aborting one, so IndexWriter closes itself.
   */
  public void testSliceAtEndOfBufferDoesNotCloseWriter() throws Exception {
    byte[] buf = buffer(12);
    try (Directory dir = newDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
      Document good = new Document();
      good.add(new StoredField("f", new BytesRef(new byte[] {42})));
      w.addDocument(good);
      Document doc = new Document();
      doc.add(new StoredField("f", new StoredFieldDataInput(new ByteArrayDataInput(buf, 4, 8))));
      Throwable t = null;
      try {
        w.addDocument(doc);
      } catch (Throwable e) {
        t = e;
      }
      try {
        if (t != null) {
          throw new AssertionError(
              "a valid 8-byte slice must be indexed without error, got: "
                  + t
                  + "; IndexWriter tragic exception: "
                  + w.getTragicException()
                  + ", open: "
                  + w.isOpen(),
              t);
        }
        assertTrue("IndexWriter must stay open", w.isOpen());
      } finally {
        w.close();
      }
    }
  }
}
