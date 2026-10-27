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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.BaseMergePolicyTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Version;

/**
 * IndexWriter#forceMerge(maxNumSegments) with TieredMergePolicy: a segment that is too large to be
 * re-merged is dropped from the candidate list but not counted, so the policy stops as soon as the
 * remaining (small) segments number maxNumSegments and the index keeps maxNumSegments + 1 (or
 * more) segments.
 */
public class TestTieredForceMergeMaxNumSegments extends BaseMergePolicyTestCase {

  @Override
  protected MergePolicy mergePolicy() {
    return new TieredMergePolicy();
  }

  @Override
  protected void assertSegmentInfos(MergePolicy policy, SegmentInfos infos) {}

  @Override
  protected void assertMerge(MergePolicy policy, MergePolicy.MergeSpecification merge) {}

  private static int countAfter(SegmentInfos infos, MergePolicy.MergeSpecification spec) {
    int count = infos.size();
    if (spec != null) {
      for (MergePolicy.OneMerge merge : spec.merges) {
        count -= merge.segments.size() - 1;
      }
    }
    return count;
  }

  /** Default TieredMergePolicy settings: one 10 GB segment and two 1 GB segments, forceMerge(2). */
  public void testForceMergeTwoDefaultSettings() throws Exception {
    TieredMergePolicy tmp = new TieredMergePolicy(); // defaults: maxMergedSegmentMB = 5 GB
    SegmentInfos infos = new SegmentInfos(Version.LATEST.major);
    infos.add(makeSegmentCommitInfo("_0", 10_000_000, 0, 10 * 1024, IndexWriter.SOURCE_MERGE));
    infos.add(makeSegmentCommitInfo("_1", 1_000_000, 0, 1024, IndexWriter.SOURCE_FLUSH));
    infos.add(makeSegmentCommitInfo("_2", 1_000_000, 0, 1024, IndexWriter.SOURCE_FLUSH));
    Map<SegmentCommitInfo, Boolean> segmentsToMerge = new HashMap<>();
    for (SegmentCommitInfo info : infos) {
      segmentsToMerge.put(info, Boolean.TRUE);
    }
    MergePolicy.MergeSpecification spec =
        tmp.findForcedMerges(
            infos, 2, segmentsToMerge, new MockMergeContext(SegmentCommitInfo::getDelCount));
    // merging _1 and _2 (2 GB together, well under every limit) would reach 2 segments
    assertTrue(
        "3 segments, maxSegmentCount=2, but the policy returned " + spec,
        countAfter(infos, spec) <= 2);
  }

  /** End to end through IndexWriter: one large segment plus three small ones, forceMerge(2). */
  public void testIndexWriterForceMergeTwo() throws Exception {
    try (Directory dir = newDirectory()) {
      Random r = new Random(42);
      IndexWriterConfig build =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, build)) {
        addDocs(w, r, 0, 1000); // one large segment (~1 MB of incompressible stored bytes)
        w.commit();
        for (int s = 0; s < 3; s++) {
          addDocs(w, r, 1000 + 10 * s, 10); // three small segments
          w.commit();
        }
      }

      TieredMergePolicy tmp = new TieredMergePolicy();
      tmp.setMaxMergedSegmentMB(0.1); // 100 KB: maxNumSegments must win (class javadoc)
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(tmp)
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        assertEquals(4, w.getSegmentCount());
        w.forceMerge(2);
        assertTrue(
            "forceMerge(2) left " + w.getSegmentCount() + " segments: " + w.cloneSegmentInfos(),
            w.getSegmentCount() <= 2);
      }
    }
  }

  /**
   * Control (passes on main): the same index with the default maxMergedSegmentMB (5 GB), so no
   * segment is excluded as too large; forceMerge(2) then reaches 2 segments.
   */
  public void testIndexWriterForceMergeTwoControl() throws Exception {
    try (Directory dir = newDirectory()) {
      Random r = new Random(42);
      IndexWriterConfig build =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, build)) {
        addDocs(w, r, 0, 1000);
        w.commit();
        for (int s = 0; s < 3; s++) {
          addDocs(w, r, 1000 + 10 * s, 10);
          w.commit();
        }
      }
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(new TieredMergePolicy())
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        assertEquals(4, w.getSegmentCount());
        w.forceMerge(2);
        assertEquals(2, w.getSegmentCount());
      }
    }
  }

  private static void addDocs(IndexWriter w, Random r, int start, int count) throws Exception {
    for (int i = start; i < start + count; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      byte[] bytes = new byte[1024];
      r.nextBytes(bytes);
      doc.add(new StoredField("payload", bytes));
      w.addDocument(doc);
    }
  }
}
