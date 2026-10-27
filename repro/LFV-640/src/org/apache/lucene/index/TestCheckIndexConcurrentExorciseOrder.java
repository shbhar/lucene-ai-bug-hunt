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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CheckIndex#exorciseIndex should only drop the broken segments. With concurrent checking (any
 * threadCount > 1, which is the default on a multi-core machine, or checkIndex(null, executor)),
 * the surviving segments are written back sorted by file size instead of in commit order, so the
 * documents of the repaired index are renumbered.
 */
public class TestCheckIndexConcurrentExorciseOrder extends LuceneTestCase {

  // segment _0: ids 0..29, _1: ids 30..32, _2 (corrupted): ids 33..42, _3: ids 43..57
  private static final int[] SEGMENT_SIZES = {30, 3, 10, 15};
  private static final int BROKEN_SEGMENT = 2;

  private static Directory buildIndexWithOneCorruptSegment() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setUseCompoundFile(false);
    int id = 0;
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int size : SEGMENT_SIZES) {
        for (int i = 0; i < size; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
          doc.add(new StoredField("idnum", id));
          doc.add(new StoredField("payload", "x".repeat(200)));
          w.addDocument(doc);
          id++;
        }
        w.commit();
      }
    }
    // Corrupt one byte in the middle of the broken segment's stored-fields data file.
    SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
    assertEquals(SEGMENT_SIZES.length, sis.size());
    SegmentCommitInfo broken = sis.info(BROKEN_SEGMENT);
    String target = null;
    for (String file : broken.files()) {
      if (file.endsWith(".fdt")) target = file;
    }
    assertNotNull(target);
    byte[] bytes;
    try (IndexInput in = dir.openInput(target, IOContext.READONCE)) {
      bytes = new byte[(int) in.length()];
      in.readBytes(bytes, 0, bytes.length);
    }
    bytes[bytes.length / 2] ^= 0x5A;
    dir.deleteFile(target);
    try (IndexOutput out = dir.createOutput(target, IOContext.DEFAULT)) {
      out.writeBytes(bytes, bytes.length);
    }
    return dir;
  }

  private static List<String> segmentNames(Directory dir) throws IOException {
    List<String> names = new ArrayList<>();
    for (SegmentCommitInfo info : SegmentInfos.readLatestCommit(dir)) names.add(info.info.name);
    return names;
  }

  private static List<Integer> idsInDocIdOrder(Directory dir) throws IOException {
    List<Integer> ids = new ArrayList<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      StoredFields sf = r.storedFields();
      for (int doc = 0; doc < r.maxDoc(); doc++) {
        ids.add(sf.document(doc).getField("idnum").numericValue().intValue());
      }
    }
    return ids;
  }

  /** What exorcise should leave: every document except those of the broken segment, in order. */
  private static List<Integer> expectedIds() {
    List<Integer> ids = new ArrayList<>();
    int id = 0;
    for (int s = 0; s < SEGMENT_SIZES.length; s++) {
      for (int i = 0; i < SEGMENT_SIZES[s]; i++, id++) {
        if (s != BROKEN_SEGMENT) ids.add(id);
      }
    }
    return ids;
  }

  private static final List<String> EXPECTED_SEGMENTS = List.of("_0", "_1", "_3");

  /** Control: sequential checking keeps the surviving segments in commit order. */
  public void testSequentialExorciseKeepsSegmentOrder() throws IOException {
    try (Directory dir = buildIndexWithOneCorruptSegment()) {
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setThreadCount(1);
        CheckIndex.Status status = checker.checkIndex();
        assertFalse(status.clean);
        assertEquals(1, status.numBadSegments);
        checker.exorciseIndex(status);
      }
      assertEquals(EXPECTED_SEGMENTS, segmentNames(dir));
      assertEquals(expectedIds(), idsInDocIdOrder(dir));
    }
  }

  /** CheckIndex#checkIndex() with threadCount > 1 (the default on a multi-core machine). */
  public void testConcurrentExorciseKeepsSegmentOrder() throws IOException {
    try (Directory dir = buildIndexWithOneCorruptSegment()) {
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setThreadCount(2);
        CheckIndex.Status status = checker.checkIndex();
        assertFalse(status.clean);
        assertEquals(1, status.numBadSegments);
        checker.exorciseIndex(status);
      }
      assertEquals("documents in doc-id order", expectedIds(), idsInDocIdOrder(dir));
      assertEquals("segments of the repaired commit", EXPECTED_SEGMENTS, segmentNames(dir));
    }
  }

  /** CheckIndex#checkIndex(List, ExecutorService) with a caller-supplied executor. */
  public void testExecutorExorciseKeepsSegmentOrder() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try (Directory dir = buildIndexWithOneCorruptSegment()) {
      try (CheckIndex checker = new CheckIndex(dir)) {
        CheckIndex.Status status = checker.checkIndex(null, executor);
        assertFalse(status.clean);
        assertEquals(1, status.numBadSegments);
        checker.exorciseIndex(status);
      }
      assertEquals("documents in doc-id order", expectedIds(), idsInDocIdOrder(dir));
      assertEquals("segments of the repaired commit", EXPECTED_SEGMENTS, segmentNames(dir));
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
