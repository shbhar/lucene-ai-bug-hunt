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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.IOUtils;

/**
 * IndexWriter accepts stored strings up to MAX_STORED_STRING_LENGTH (715,827,876) chars, and the
 * stored fields writer stores them. Reading one back goes through DataInput#readString, which
 * decodes the UTF-8 bytes with new String(bytes, 0, length, UTF_8). The JDK sizes that decoding
 * buffer by the byte count and refuses 2^30 - 1 or more UTF-16 units, so a stored string whose
 * UTF-8 form is 2^30 - 1 bytes or longer (and is not Latin-1) cannot be read back: every
 * StoredFields#document call on it throws OutOfMemoryError, whatever the heap size.
 *
 * <p>Needs a heap of about 6 GB.
 */
public class TestStoredStringOver1GiBUtf8 extends LuceneTestCase {

  private static void assertRoundTrip(int chars) throws Exception {
    assertTrue(chars <= IndexWriter.MAX_STORED_STRING_LENGTH);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        // U+4E00 is 3 bytes in UTF-8: the stored value is 3 * chars bytes
        doc.add(new StoredField("f", "\u4e00".repeat(chars)));
        w.addDocument(doc);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        String value;
        try {
          value = r.storedFields().document(0).get("f");
        } catch (OutOfMemoryError e) {
          throw new AssertionError(
              "reading back a stored string of " + chars + " chars threw " + e, e);
        }
        assertNotNull(value);
        assertEquals(chars, value.length());
        assertEquals('\u4e00', value.charAt(chars - 1));
      }
    }
  }

  /** Control: 3 * 357,913,940 = 1,073,741,820 UTF-8 bytes (below 2^30 - 1) reads back. */
  public void testUtf8LengthBelow2Pow30Minus1() throws Exception {
    assertRoundTrip(357_913_940);
  }

  /**
   * One more char: 1,073,741,823 = 2^30 - 1 UTF-8 bytes, half of what IndexWriter accepts
   * (MAX_STORED_STRING_LENGTH = 715,827,876 chars).
   */
  public void testUtf8LengthAt2Pow30Minus1() throws Exception {
    assertRoundTrip(357_913_941);
  }

  /**
   * The same document added to another index whose field numbers differ: the merge copies stored
   * fields through a visitor, reads the string, and the OutOfMemoryError closes the IndexWriter.
   */
  public void testAddIndexesWithOtherFieldNumbers() throws Exception {
    int chars = 357_913_941;
    try (Directory source = new ByteBuffersDirectory();
        Directory target = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(source, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        doc.add(new StoredField("f", "\u4e00".repeat(chars))); // field number 0 here
        w.addDocument(doc);
        w.commit();
      }
      IndexWriter w =
          new IndexWriter(target, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
      try {
        Document first = new Document();
        first.add(new StringField("id", "0", Field.Store.YES)); // takes field number 0
        w.addDocument(first);
        Throwable t = null;
        try (DirectoryReader r = DirectoryReader.open(source)) {
          w.addIndexes((CodecReader) r.leaves().get(0).reader());
        } catch (Throwable e) {
          t = e;
        }
        assertNull(
            "addIndexes threw "
                + t
                + "; IndexWriter tragic exception: "
                + w.getTragicException()
                + ", open: "
                + w.isOpen(),
            t);
        assertTrue(w.isOpen());
      } finally {
        IOUtils.closeWhileHandlingException(w);
      }
    }
  }
}
