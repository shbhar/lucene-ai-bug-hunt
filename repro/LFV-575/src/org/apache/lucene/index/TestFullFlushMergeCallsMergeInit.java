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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A merge that a merge policy returns from findFullFlushMerges (merge-on-commit / merge-on-refresh)
 * gets mergeFinished from IndexWriter but never mergeInit, although OneMerge#mergeInit is
 * documented as "Called by IndexWriter after the merge started and from the thread that will be
 * executing the merge". The same OneMerge returned from findForcedMerges gets both.
 *
 * <p>Public API only.
 */
public class TestFullFlushMergeCallsMergeInit extends LuceneTestCase {

  static final class RecordingMerge extends MergePolicy.OneMerge {
    final AtomicInteger mergeInitCalls = new AtomicInteger();
    final AtomicInteger mergeFinishedCalls = new AtomicInteger();

    RecordingMerge(List<SegmentCommitInfo> segments) {
      super(segments);
    }

    @Override
    public void mergeInit() throws IOException {
      mergeInitCalls.incrementAndGet();
      super.mergeInit();
    }

    @Override
    public void mergeFinished(boolean success, boolean segmentDropped) throws IOException {
      mergeFinishedCalls.incrementAndGet();
      super.mergeFinished(success, segmentDropped);
    }
  }

  /** Merges all segments that are not merging yet, from findFullFlushMerges or findForcedMerges. */
  static final class AllSegmentsPolicy extends FilterMergePolicy {
    final List<RecordingMerge> returned = Collections.synchronizedList(new ArrayList<>());

    AllSegmentsPolicy() {
      super(new LogDocMergePolicy());
    }

    private MergeSpecification mergeAll(SegmentInfos infos, MergeContext context) {
      List<SegmentCommitInfo> segments = new ArrayList<>();
      for (SegmentCommitInfo sci : infos) {
        if (context.getMergingSegments().contains(sci) == false) {
          segments.add(sci);
        }
      }
      if (segments.size() < 2) {
        return null;
      }
      RecordingMerge merge = new RecordingMerge(segments);
      returned.add(merge);
      MergeSpecification spec = new MergeSpecification();
      spec.add(merge);
      return spec;
    }

    @Override
    public MergeSpecification findMerges(
        MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
      return null;
    }

    @Override
    public MergeSpecification findFullFlushMerges(
        MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
      return mergeAll(infos, context);
    }

    @Override
    public MergeSpecification findForcedMerges(
        SegmentInfos infos,
        int maxSegmentCount,
        Map<SegmentCommitInfo, Boolean> segmentsToMerge,
        MergeContext context) {
      return mergeAll(infos, context);
    }
  }

  private static IndexWriterConfig config(MergePolicy mp) {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxFullFlushMergeWaitMillis(60_000);
    return iwc;
  }

  private static void addDoc(IndexWriter w, String id) throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    w.addDocument(doc);
  }

  private static void assertLifecycle(String path, AllSegmentsPolicy mp) {
    assertEquals(path + ": merges returned by the policy", 1, mp.returned.size());
    RecordingMerge merge = mp.returned.get(0);
    assertEquals(path + ": mergeFinished calls", 1, merge.mergeFinishedCalls.get());
    assertEquals(path + ": mergeInit calls", 1, merge.mergeInitCalls.get());
  }

  public void testMergeOnCommit() throws Exception {
    AllSegmentsPolicy mp = new AllSegmentsPolicy();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config(mp))) {
      addDoc(w, "1");
      w.commit();
      addDoc(w, "2");
      w.commit(); // findFullFlushMerges(COMMIT) merges the two segments
      assertEquals(1, SegmentInfos.readLatestCommit(dir).size());
      assertLifecycle("merge on commit", mp);
    }
  }

  public void testMergeOnGetReader() throws Exception {
    AllSegmentsPolicy mp = new AllSegmentsPolicy();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config(mp))) {
      addDoc(w, "1");
      w.flush();
      addDoc(w, "2");
      try (DirectoryReader r = DirectoryReader.open(w)) { // findFullFlushMerges(GET_READER)
        assertEquals(1, r.leaves().size());
      }
      assertLifecycle("merge on getReader", mp);
    }
  }

  /** Control: the same OneMerge through forceMerge gets both callbacks. */
  public void testForcedMergeControl() throws Exception {
    AllSegmentsPolicy mp = new AllSegmentsPolicy();
    IndexWriterConfig iwc = config(mp);
    iwc.setMaxFullFlushMergeWaitMillis(0);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      addDoc(w, "1");
      w.flush();
      addDoc(w, "2");
      w.flush();
      w.forceMerge(1);
      assertLifecycle("forceMerge", mp);
    }
  }
}
