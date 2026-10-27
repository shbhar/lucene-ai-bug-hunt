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
package org.apache.lucene.misc.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.SoftDeletesRetentionMergePolicy;
import org.apache.lucene.index.Sorter;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SoftDeletesRetentionMergePolicy wraps every OneMerge of the policy it wraps, but its wrapper only
 * delegates wrapForMerge. OneMerge#reorder, #setMergeInfo, #mergeInit and #mergeFinished of the
 * wrapped merge are never called, so a reordering policy below it (BPReorderingMergePolicy) silently
 * stops reordering forced and natural merges, and its "bp.reordered" diagnostic is never written.
 * IndexWriter's own OneMerge wrapper (commit/getReader merges) delegates these methods, with the
 * comment "must delegate".
 */
public class TestSoftDeletesRetentionDropsReorder extends LuceneTestCase {

  private static BPIndexReorderer reorderer() {
    BPIndexReorderer r = new BPIndexReorderer();
    r.setMinDocFreq(2);
    r.setMinPartitionSize(2);
    return r;
  }

  /** Same documents as TestBPReorderingMergePolicy: ids in order, a low-cardinality body. */
  private static void index(IndexWriter w) throws IOException {
    for (int i = 0; i < 400; ++i) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Store.YES));
      doc.add(new StringField("body", Integer.toString(i % 2 == 0 ? 0 : i % 10), Store.YES));
      w.addDocument(doc);
      if (i % 100 == 99) {
        w.flush();
      }
    }
  }

  /** Runs forceMerge(1) and returns {reordered diagnostic, true if stored ids are not ascending}. */
  private static String[] forceMergeAndInspect(MergePolicy mp) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setSoftDeletesField("soft")
              .setMergePolicy(mp)
              .setMergeScheduler(new SerialMergeScheduler())
              .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
              .setRAMBufferSizeMB(64);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        index(w);
        w.forceMerge(1);
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(1, r.leaves().size());
          SegmentReader sr = (SegmentReader) r.leaves().get(0).reader();
          String diag =
              sr.getSegmentInfo().info.getDiagnostics().get(BPReorderingMergePolicy.REORDERED);
          StoredFields sf = sr.storedFields();
          boolean outOfOrder = false;
          int prev = -1;
          for (int i = 0; i < sr.maxDoc(); i++) {
            int id = Integer.parseInt(sf.document(i).get("id"));
            if (id < prev) {
              outOfOrder = true;
              break;
            }
            prev = id;
          }
          return new String[] {diag, Boolean.toString(outOfOrder)};
        }
      }
    }
  }

  /** Control: reordering policy outermost, retention policy inside: reordering happens. */
  public void testReorderingPolicyOutermost() throws IOException {
    MergePolicy mp =
        new BPReorderingMergePolicy(
            new SoftDeletesRetentionMergePolicy(
                "soft", MatchAllDocsQuery::new, new LogDocMergePolicy()),
            reorderer());
    String[] got = forceMergeAndInspect(mp);
    assertEquals("bp.reordered=true docsReordered=true", "bp.reordered=" + got[0] + " docsReordered=" + got[1]);
  }

  /** Retention policy outermost (wrapping the reordering policy): reordering must still happen. */
  public void testRetentionPolicyOutermost() throws IOException {
    MergePolicy mp =
        new SoftDeletesRetentionMergePolicy(
            "soft",
            MatchAllDocsQuery::new,
            new BPReorderingMergePolicy(new LogDocMergePolicy(), reorderer()));
    String[] got = forceMergeAndInspect(mp);
    assertEquals(
        "forced merge through SoftDeletesRetentionMergePolicy(BPReorderingMergePolicy)",
        "bp.reordered=true docsReordered=true",
        "bp.reordered=" + got[0] + " docsReordered=" + got[1]);
  }

  /**
   * Generic form: the OneMerge callbacks of the wrapped policy's merges are not called once the
   * merges pass through SoftDeletesRetentionMergePolicy.
   */
  public void testWrappedOneMergeCallbacks() throws IOException {
    AtomicInteger reorder = new AtomicInteger(), setInfo = new AtomicInteger();
    AtomicInteger init = new AtomicInteger(), finished = new AtomicInteger();
    MergePolicy recording =
        new FilterMergePolicy(new LogDocMergePolicy()) {
          @Override
          public MergeSpecification findForcedMerges(
              SegmentInfos infos,
              int maxSegmentCount,
              Map<SegmentCommitInfo, Boolean> segmentsToMerge,
              MergeContext ctx)
              throws IOException {
            MergeSpecification spec =
                super.findForcedMerges(infos, maxSegmentCount, segmentsToMerge, ctx);
            if (spec == null) {
              return null;
            }
            MergeSpecification out = new MergeSpecification();
            for (OneMerge m : spec.merges) {
              List<SegmentCommitInfo> segs = new ArrayList<>(m.segments);
              out.add(
                  new OneMerge(segs) {
                    @Override
                    public void mergeInit() throws IOException {
                      init.incrementAndGet();
                      super.mergeInit();
                    }

                    @Override
                    public Sorter.DocMap reorder(CodecReader reader, Directory dir, Executor ex) {
                      reorder.incrementAndGet();
                      return null;
                    }

                    @Override
                    public void setMergeInfo(SegmentCommitInfo info) {
                      setInfo.incrementAndGet();
                      super.setMergeInfo(info);
                    }

                    @Override
                    public void mergeFinished(boolean success, boolean segmentDropped) {
                      finished.incrementAndGet();
                    }
                  });
            }
            return out;
          }
        };
    forceMergeAndInspect(
        new SoftDeletesRetentionMergePolicy("soft", MatchAllDocsQuery::new, recording));
    String calls =
        "reorder="
            + reorder.get()
            + " setMergeInfo="
            + setInfo.get()
            + " mergeInit="
            + init.get()
            + " mergeFinished="
            + finished.get();
    assertTrue("wrapped OneMerge callbacks: " + calls, reorder.get() > 0);
    assertTrue("wrapped OneMerge callbacks: " + calls, setInfo.get() > 0);
    assertTrue("wrapped OneMerge callbacks: " + calls, finished.get() > 0);
  }
}
