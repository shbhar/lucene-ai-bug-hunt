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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Security-assessment probe for StoredFieldDataInput(ByteArrayDataInput). Each test asserts the
 * correct behaviour; on stock main the exposure tests are expected to fail. Uses public API only and
 * the default codec.
 */
public class TestStoredFieldDataInputExposureProbe extends LuceneTestCase {

  private static String hex(BytesRef b) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < b.length; i++) {
      sb.append(String.format("%02x", b.bytes[b.offset + i] & 0xff));
    }
    return sb.toString();
  }

  private static String ascii(BytesRef b) {
    return new String(b.bytes, b.offset, b.length, StandardCharsets.ISO_8859_1);
  }

  /**
   * One application buffer holds two records owned by different users. Doc A is indexed from a
   * slice of record A. A reader restricted to owner:alice must see only record A's bytes.
   */
  public void testSharedBufferRecordOfAnotherOwner() throws Exception {
    byte[] header = "HDR:16-byte-head".getBytes(StandardCharsets.US_ASCII); // 16 bytes
    byte[] recA = "alice:public-txt".getBytes(StandardCharsets.US_ASCII); // 16 bytes
    byte[] recB = "bob:SECRET-TOKEN".getBytes(StandardCharsets.US_ASCII); // 16 bytes
    byte[] buf = new byte[128];
    System.arraycopy(header, 0, buf, 0, 16);
    System.arraycopy(recA, 0, buf, 16, 16);
    System.arraycopy(recB, 0, buf, 32, 16);
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document a = new Document();
      a.add(new StringField("owner", "alice", Field.Store.NO));
      a.add(
          new StoredField("payload", new StoredFieldDataInput(new ByteArrayDataInput(buf, 16, 16))));
      w.addDocument(a);
      w.commit();
      Document b = new Document();
      b.add(new StringField("owner", "bob", Field.Store.NO));
      b.add(new StoredField("payload", new BytesRef(recB)));
      w.addDocument(b);
      w.commit();
      int segsBefore;
      try (DirectoryReader r0 = DirectoryReader.open(dir)) {
        segsBefore = r0.leaves().size();
      }
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        System.out.println(
            "PROBE shared-buffer: segments before forceMerge=" + segsBefore + " after=" + r.leaves().size());
        assertEquals(2, segsBefore);
        assertEquals(1, r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        TopDocs td = s.search(new TermQuery(new Term("owner", "alice")), 10);
        assertEquals(1, td.totalHits.value());
        BytesRef stored = s.storedFields().document(td.scoreDocs[0].doc).getBinaryValue("payload");
        System.out.println(
            "PROBE shared-buffer: stored length="
                + stored.length
                + " ascii='"
                + ascii(stored)
                + "' (after forceMerge(1), reader restricted to owner:alice)");
        assertEquals(
            "alice's doc must hold only record A (16 bytes)", new BytesRef(recA), stored);
      }
    }
  }

  /**
   * A pooled buffer is reused: request 1 filled it, request 2 writes a 4-byte header plus a 12-byte
   * value and slices the value. The stored field must not contain request 1's leftover bytes.
   */
  public void testReusedBufferStaleBytes() throws Exception {
    byte[] pool = new byte[64];
    byte[] req1 = "req1:user=carol;password=hunter2;session=0123456789abcdef-ZZZZZZZ".getBytes(
        StandardCharsets.US_ASCII);
    System.arraycopy(req1, 0, pool, 0, Math.min(req1.length, pool.length));
    byte[] req2 = "HDR4twelve-bytes".getBytes(StandardCharsets.US_ASCII); // 16 bytes
    System.arraycopy(req2, 0, pool, 0, 16);
    ByteArrayDataInput reused = new ByteArrayDataInput();
    reused.reset(pool, 4, 12);
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document d = new Document();
      d.add(new StoredField("payload", new StoredFieldDataInput(reused)));
      w.addDocument(d);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        BytesRef stored = r.storedFields().document(0).getBinaryValue("payload");
        System.out.println(
            "PROBE reused-buffer: stored length=" + stored.length + " ascii='" + ascii(stored) + "'");
        assertEquals(
            "stored value must be request 2's 12-byte value",
            new BytesRef(Arrays.copyOfRange(pool, 4, 16)),
            stored);
      }
    }
  }

  /**
   * Offset 0, but the application first reads a 4-byte header from the input. The remaining 16
   * bytes are the value; the input's limit is 20 inside a 64-byte array.
   */
  public void testPartlyReadInputAtOffsetZero() throws Exception {
    byte[] buf = new byte[64];
    for (int i = 0; i < buf.length; i++) {
      buf[i] = (byte) (i + 1);
    }
    ByteArrayDataInput in = new ByteArrayDataInput(buf, 0, 20);
    in.readInt(); // 4-byte header consumed by the application
    StoredFieldDataInput sfdi = new StoredFieldDataInput(in);
    System.out.println(
        "PROBE partly-read: position=" + in.getPosition() + " sfdi.length=" + sfdi.getLength());
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document d = new Document();
      d.add(new StoredField("payload", sfdi));
      w.addDocument(d);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        BytesRef stored = r.storedFields().document(0).getBinaryValue("payload");
        System.out.println("PROBE partly-read: stored length=" + stored.length + " hex=" + hex(stored));
        assertEquals(
            "stored value must be the 16 unread bytes [4, 20)",
            new BytesRef(Arrays.copyOfRange(buf, 4, 20)),
            stored);
      }
    }
  }

  /**
   * Detection: the over-long value is a well-formed stored field, so CheckIndex should report the
   * index as clean. This test is expected to PASS on stock main (it documents that nothing flags
   * it); it prints the stored length so the over-read is visible in the log.
   */
  public void testCheckIndexDoesNotFlagIt() throws Exception {
    byte[] buf = new byte[40];
    for (int i = 0; i < buf.length; i++) {
      buf[i] = (byte) (i + 1);
    }
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document d = new Document();
        d.add(
            new StoredField("payload", new StoredFieldDataInput(new ByteArrayDataInput(buf, 8, 8))));
        w.addDocument(d);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        BytesRef stored = r.storedFields().document(0).getBinaryValue("payload");
        System.out.println("PROBE checkindex: stored length=" + stored.length + " (slice was 8)");
      }
      CheckIndex.Status status = TestUtil.checkIndex(dir);
      System.out.println("PROBE checkindex: clean=" + status.clean);
      assertTrue("CheckIndex clean", status.clean);
    }
  }
}
