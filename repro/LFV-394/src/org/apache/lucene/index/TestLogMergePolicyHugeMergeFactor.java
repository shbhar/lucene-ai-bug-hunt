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
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.BaseMergePolicyTestCase;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

/**
 * LogMergePolicy.setMergeFactor accepts any value >= 2, but findMerges computes the end of each
 * candidate merge window as {@code int end = start + mergeFactor}. With mergeFactor =
 * Integer.MAX_VALUE that sum wraps negative for every level that does not start at segment 0, the
 * window loop is entered with a negative end, and the minimum-merge-size packing step calls
 * levels.get(end) with that negative index. Two segments whose doc counts differ by a factor of
 * about 10^7 already form two levels at this merge factor.
 */
public class TestLogMergePolicyHugeMergeFactor extends LuceneTestCase {

  private static SegmentCommitInfo segment(Directory dir, String name, int maxDoc) {
    SegmentInfo info =
        new SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            name,
            maxDoc,
            false,
            false,
            TestUtil.getDefaultCodec(),
            Collections.emptyMap(),
            StringHelper.randomId(),
            Collections.singletonMap(IndexWriter.SOURCE, IndexWriter.SOURCE_FLUSH),
            null);
    info.setFiles(Collections.emptySet());
    return new SegmentCommitInfo(info, 0, 0, -1, -1, -1, StringHelper.randomId());
  }

  private static MergePolicy.MergeSpecification findMerges(int mergeFactor) throws IOException {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(mergeFactor);
    SegmentInfos infos = new SegmentInfos(Version.LATEST.major);
    try (Directory dir = new ByteBuffersDirectory()) {
      infos.add(segment(dir, "_0", 20_000_000)); // level 0: log(2e7) / log(mergeFactor) ~ 0.78
      infos.add(segment(dir, "_1", 1)); // level 1 starts at segment 1
      return mp.findMerges(
          MergeTrigger.FULL_FLUSH, infos, new BaseMergePolicyTestCase.MockMergeContext(s -> 0));
    }
  }

  /** Control: one less than Integer.MAX_VALUE does not wrap for a level starting at segment 1. */
  public void testFindMergesMergeFactorMaxMinusOne() throws IOException {
    assertNull(findMerges(Integer.MAX_VALUE - 1));
  }

  /** findMerges on the same two segments with mergeFactor = Integer.MAX_VALUE. */
  public void testFindMergesMergeFactorMax() throws IOException {
    assertNull(findMerges(Integer.MAX_VALUE)); // two segments: nothing to merge
  }

  /** End to end: once two such segments exist, no commit or flush can succeed any more. */
  public void testIndexWriterCommitAfterBulkLoad() throws IOException {
    try (Directory dir = newFSDirectory(createTempDir())) {
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMergeFactor(Integer.MAX_VALUE); // "larger values (> 10) are best for batch index creation"
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(mp)
              .setRAMBufferSizeMB(256)
              .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        List<Document> batch = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
          batch.add(new Document());
        }
        for (int i = 0; i < 20_000; i++) {
          w.addDocuments(batch); // 20 million empty documents, one flushed segment
        }
        w.commit();
        assertEquals(1, SegmentInfos.readLatestCommit(dir).size());
        w.addDocument(new Document());
        w.commit(); // expected: the new document is committed as a second segment
        assertEquals(20_000_001, SegmentInfos.readLatestCommit(dir).totalMaxDoc());
      }
    }
  }
}
