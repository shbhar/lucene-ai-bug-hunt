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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * Lucene90CompressingStoredFieldsWriter keeps the end offset of every buffered document of the
 * current chunk as an int (Math.toIntExact(bufferedDocs.size()) in finishDocument). Documents are
 * buffered until the chunk reaches chunkSize (81,920 bytes for BEST_SPEED), so a document is
 * appended after up to 81,919 bytes of earlier documents. A document of S bytes then fails when
 * those earlier bytes plus S exceed Integer.MAX_VALUE, although S is below the documented limit of
 * 2^31 - 2^14 bytes and the same document is accepted when it starts a chunk. The
 * ArithmeticException is an aborting exception, so IndexWriter closes itself.
 *
 * <p>Needs a heap of about 6 GB.
 */
public class TestStoredFieldsChunkEndOffsetOverflow extends LuceneTestCase {

  // serialized size of a document with one binary stored field of n bytes (field number 0):
  // 1 byte for number and type + the vint length (3 bytes below 2^21, 5 bytes from 2^28) + n
  private static final int SMALL = 80_000; // serialized 80,004 bytes, below chunkSize
  private static final int SMALL_SERIALIZED = 1 + 3 + SMALL;
  // big document: earlier bytes + its serialized size = 2^31, one more than Integer.MAX_VALUE
  private static final int BIG_SERIALIZED = (int) ((1L << 31) - SMALL_SERIALIZED);
  private static final int BIG = BIG_SERIALIZED - (1 + 5);
  private static final long DOCUMENTED_LIMIT = (1L << 31) - (1L << 14);

  private static Throwable addDocs(byte[] big, boolean smallFirst) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
      try {
        if (smallFirst) {
          Document small = new Document();
          small.add(new StoredField("f", new BytesRef(new byte[SMALL])));
          w.addDocument(small);
        }
        Document doc = new Document();
        doc.add(new StoredField("f", new BytesRef(big)));
        try {
          w.addDocument(doc);
        } catch (Throwable t) {
          return new AssertionError(
              "addDocument threw " + t + "; tragic=" + w.getTragicException()
                  + ", open=" + w.isOpen(), t);
        }
        w.commit();
        return null;
      } finally {
        IOUtils.closeWhileHandlingException(w);
      }
    }
  }

  public void testBigDocumentAfterSmallDocument() throws Exception {
    assertTrue(BIG_SERIALIZED < DOCUMENTED_LIMIT);
    byte[] big = new byte[BIG];
    // control: the same document starting a chunk is indexed
    assertNull(addDocs(big, false));
    // after one small document in the same chunk it fails
    Throwable t = addDocs(big, true);
    if (t != null) {
      throw (AssertionError) t;
    }
  }
}
