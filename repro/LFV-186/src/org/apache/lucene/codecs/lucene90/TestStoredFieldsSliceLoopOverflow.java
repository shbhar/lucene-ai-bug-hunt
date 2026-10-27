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
package org.apache.lucene.codecs.lucene90;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * Lucene90CompressingStoredFieldsWriter#flush compresses a chunk of 2 * chunkSize bytes or more in
 * slices with {@code for (int compressed = 0; compressed < capacity; compressed += chunkSize)}. For
 * a chunk of more than (Integer.MAX_VALUE / chunkSize) * chunkSize = 2,147,450,880 bytes (BEST_SPEED,
 * chunkSize 81,920) the int loop variable wraps after the last slice, the next slice() call gets a
 * negative offset and throws, and the exception closes the IndexWriter.
 *
 * <p>A chunk holds the documents buffered since the last flush (less than chunkSize bytes) plus the
 * document that fills it. So a document at the limit documented by the default codec's format,
 * 2^31 - chunkSize = 2,147,401,728 bytes (Lucene90CompressingStoredFieldsFormat; the outer
 * Lucene90StoredFieldsFormat documents 2^31 - 2^14), fails as soon as at least 49,153 bytes of
 * earlier documents are buffered in the same chunk; with 49,152 it is indexed.
 *
 * <p>Needs a heap of about 8 GB (one 2 GB value, the writer's 2 GB buffer, the 2 GB read back).
 */
public class TestStoredFieldsSliceLoopOverflow extends LuceneTestCase {

  private static final int CHUNK_SIZE = 10 * 8 * 1024; // BEST_SPEED
  // largest chunk the slice loop handles: (Integer.MAX_VALUE / 81920) * 81920 = 2,147,450,880
  private static final long LAST_GOOD_CHUNK = (Integer.MAX_VALUE / CHUNK_SIZE) * (long) CHUNK_SIZE;
  // the documented limit of Lucene90CompressingStoredFieldsFormat for this chunk size
  private static final long DELEGATE_LIMIT = (1L << 31) - CHUNK_SIZE; // 2,147,401,728
  // serialized size of a document with one binary stored field number 0 of n bytes:
  // 1 byte for the field number and type + the vint length + n
  private static final int BIG = (int) (DELEGATE_LIMIT - (1 + 5)); // vint of BIG is 5 bytes

  private static int smallValueLength(long serialized) {
    return (int) (serialized - (1 + 3)); // vint of a length in [2^14, 2^21) is 3 bytes
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
  }

  /** Reads document {@code doc} back and returns the length of its only stored field. */
  private static long storedLength(Directory dir, int doc) throws IOException {
    long[] length = {-1};
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      r.storedFields()
          .document(
              doc,
              new StoredFieldVisitor() {
                @Override
                public Status needsField(FieldInfo fieldInfo) {
                  return Status.YES;
                }

                @Override
                public void binaryField(FieldInfo fieldInfo, byte[] value) {
                  length[0] = value.length;
                }
              });
    }
    return length[0];
  }

  /**
   * Indexes a small document whose serialized size is {@code earlierBytes} (it stays buffered,
   * being below chunkSize), then the big document, and checks that both are indexed and read back.
   */
  private static void assertSmallThenBig(byte[] big, long earlierBytes) throws IOException {
    assertSmallThenBig(big, earlierBytes, BIG);
  }

  private static void assertSmallThenBig(byte[] big, long earlierBytes, int bigLength)
      throws IOException {
    assertTrue(earlierBytes < CHUNK_SIZE);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      try {
        Document small = new Document();
        small.add(new StoredField("f", new BytesRef(new byte[smallValueLength(earlierBytes)])));
        w.addDocument(small);
        Document doc = new Document();
        doc.add(new StoredField("f", new BytesRef(big, 0, bigLength)));
        try {
          w.addDocument(doc);
        } catch (Throwable t) {
          throw new AssertionError(
              "addDocument of a document of "
                  + (bigLength + 6)
                  + " bytes (documented limit "
                  + DELEGATE_LIMIT
                  + ") after "
                  + earlierBytes
                  + " bytes of earlier documents threw "
                  + t
                  + "; IndexWriter tragic exception: "
                  + w.getTragicException()
                  + ", open: "
                  + w.isOpen(),
              t);
        }
        w.commit();
      } finally {
        IOUtils.closeWhileHandlingException(w);
      }
      assertEquals(bigLength, storedLength(dir, 1));
    }
  }

  /** Control: chunk of exactly LAST_GOOD_CHUNK bytes (49,152 earlier bytes) is written and read. */
  public void testDocumentAtLimitAfter49152EarlierBytes() throws Exception {
    assertEquals(LAST_GOOD_CHUNK, 49_152 + DELEGATE_LIMIT);
    assertIndexed(49_152);
  }

  /** One more earlier byte: the chunk is LAST_GOOD_CHUNK + 1 bytes and the slice loop wraps. */
  public void testDocumentAtLimitAfter49153EarlierBytes() throws Exception {
    assertIndexed(49_153);
  }

  /**
   * The same with a document one byte below the documented limit, in case "larger than" is read
   * as exclusive: the chunk is again LAST_GOOD_CHUNK + 1 bytes.
   */
  public void testDocumentBelowLimitAfter49154EarlierBytes() throws Exception {
    byte[] big = new byte[BIG];
    assertSmallThenBig(big, 49_154, BIG - 1);
  }

  private static void assertIndexed(long earlierBytes) throws IOException {
    byte[] big = new byte[BIG];
    assertSmallThenBig(big, earlierBytes);
  }
}
