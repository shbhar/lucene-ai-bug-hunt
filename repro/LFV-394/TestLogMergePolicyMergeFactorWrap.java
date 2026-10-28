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
import java.util.Collections;
import java.util.Set;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

/**
 * LogMergePolicy.setMergeFactor accepts any value >= 2. findMerges computes the end of a candidate
 * window as {@code start + mergeFactor} in int arithmetic. These tests check that findMerges (and
 * IndexWriter.commit, which calls it) still work for merge factors close to Integer.MAX_VALUE once
 * the segments fall into more than one level.
 *
 * <p>Uses only public API (SegmentInfo / SegmentCommitInfo / SegmentInfos constructors,
 * MergePolicy.MergeContext, LogDocMergePolicy, IndexWriter).
 */
public class TestLogMergePolicyMergeFactorWrap extends LuceneTestCase {

  /** A merge context with no deletes and no running merges. */
  private static final MergePolicy.MergeContext NO_DELETES_NO_MERGES =
      new MergePolicy.MergeContext() {
        @Override
        public int numDeletesToMerge(SegmentCommitInfo info) {
          return 0;
        }

        @Override
        public int numDeletedDocs(SegmentCommitInfo info) {
          return 0;
        }

        @Override
        public InfoStream getInfoStream() {
          return InfoStream.NO_OUTPUT;
        }

        @Override
        public Set<SegmentCommitInfo> getMergingSegments() {
          return Collections.emptySet();
        }
      };

  private static SegmentCommitInfo segment(Directory dir, String name, int maxDoc) {
    SegmentInfo si =
        new SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            name,
            maxDoc,
            false,
            false,
            Codec.getDefault(),
            Collections.emptyMap(),
            StringHelper.randomId(),
            Collections.emptyMap(),
            null);
    return new SegmentCommitInfo(si, 0, 0, -1, -1, -1, StringHelper.randomId());
  }

  /** Runs LogDocMergePolicy.findMerges on segments with the given doc counts (in index order). */
  private static MergePolicy.MergeSpecification findMerges(int mergeFactor, int... maxDocs)
      throws IOException {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(mergeFactor); // accepted: the only validation is mergeFactor >= 2
    assertEquals(mergeFactor, mp.getMergeFactor());
    try (Directory dir = new ByteBuffersDirectory()) {
      SegmentInfos infos = new SegmentInfos(Version.LATEST.major);
      for (int i = 0; i < maxDocs.length; i++) {
        infos.add(segment(dir, "_" + i, maxDocs[i]));
      }
      return mp.findMerges(MergeTrigger.SEGMENT_FLUSH, infos, NO_DELETES_NO_MERGES);
    }
  }

  private static void assertNoMergeAndNoException(int mergeFactor, int... maxDocs)
      throws IOException {
    MergePolicy.MergeSpecification spec;
    try {
      spec = findMerges(mergeFactor, maxDocs);
    } catch (RuntimeException | Error e) {
      throw new AssertionError(
          "findMerges threw for mergeFactor="
              + mergeFactor
              + " and segment doc counts "
              + java.util.Arrays.toString(maxDocs),
          e);
    }
    // Every level here holds fewer than mergeFactor segments, so no natural merge is possible.
    assertNull("no merge expected", spec);
  }

  /** Control: one below Integer.MAX_VALUE, same two segments (start of the 2nd level = 1). */
  public void testControlMaxMinusOneTwoLevels() throws IOException {
    assertNoMergeAndNoException(Integer.MAX_VALUE - 1, 20_000_000, 1);
  }

  /** Control: Integer.MAX_VALUE with all segments in a single level (start stays 0). */
  public void testControlMaxSingleLevel() throws IOException {
    assertNoMergeAndNoException(Integer.MAX_VALUE, 20_000_000, 19_000_000, 18_000_000);
  }

  /** Integer.MAX_VALUE, one big and one tiny segment: the 2nd level starts at index 1. */
  public void testMaxMergeFactorTwoLevels() throws IOException {
    assertNoMergeAndNoException(Integer.MAX_VALUE, 20_000_000, 1);
  }

  /**
   * Integer.MAX_VALUE - 1 also fails once the 2nd level starts at index 2: the wrap condition is
   * mergeFactor > Integer.MAX_VALUE - start, not mergeFactor == Integer.MAX_VALUE.
   */
  public void testMaxMinusOneSecondLevelAtIndexTwo() throws IOException {
    assertNoMergeAndNoException(Integer.MAX_VALUE - 1, 20_000_000, 20_000_000, 1);
  }

  /**
   * End to end: one bulk segment of 12M empty documents, then a 1-document flush. The second
   * commit must succeed and make all documents visible.
   */
  public void testIndexWriterCommitAfterBulkSegment() throws IOException {
    final int bulkDocs = 12_000_000; // > Integer.MAX_VALUE^0.75 ~ 1.0e7, so the 1-doc flush is a new level
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(Integer.MAX_VALUE);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(mp)
              .setMergeScheduler(new SerialMergeScheduler())
              .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
              .setRAMBufferSizeMB(1024);
      IndexWriter w = new IndexWriter(dir, iwc);
      Throwable failure = null;
      try {
        Document empty = new Document();
        for (int i = 0; i < bulkDocs; i++) {
          w.addDocument(empty);
        }
        w.commit();
        // Precondition (not the bug): the bulk load is a single segment.
        SegmentInfos afterBulk = SegmentInfos.readLatestCommit(dir);
        assertEquals("precondition: one bulk segment", 1, afterBulk.size());
        assertEquals("precondition: bulk segment size", bulkDocs, afterBulk.info(0).info.maxDoc());

        w.addDocument(empty);
        w.commit(); // the contract: commit makes the new document durable
      } catch (RuntimeException | Error e) {
        failure = e;
      }
      if (failure != null) {
        w.rollback();
        SegmentInfos latest = SegmentInfos.readLatestCommit(dir);
        throw new AssertionError(
            "IndexWriter.commit() threw with LogDocMergePolicy mergeFactor=Integer.MAX_VALUE; latest"
                + " commit has "
                + latest.totalMaxDoc()
                + " docs in "
                + latest.size()
                + " segment(s), expected "
                + (bulkDocs + 1),
            failure);
      }
      w.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(bulkDocs + 1, r.numDocs());
      }
    }
  }
}
