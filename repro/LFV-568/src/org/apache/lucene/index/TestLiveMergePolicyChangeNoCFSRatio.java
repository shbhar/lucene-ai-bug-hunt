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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Release-API variant (Lucene 9.x/10.x, where MergePolicy still owns the compound-file ratio) of
 * TestLiveMergePolicyChange#testRegisteredMergeKeepsCompoundFileDecisionOfRegisteringPolicy.
 */
public class TestLiveMergePolicyChangeNoCFSRatio extends LuceneTestCase {

  private static final class OnDemandMergeScheduler extends MergeScheduler {
    private MergeSource source;

    @Override
    public synchronized void merge(MergeSource mergeSource, MergeTrigger trigger) {
      source = mergeSource;
    }

    synchronized void runPendingMerges() throws IOException {
      MergePolicy.OneMerge merge;
      while ((merge = source.getNextMerge()) != null) {
        try {
          source.merge(merge);
        } finally {
          source.onMergeFinished(merge);
        }
      }
    }

    @Override
    public void close() {}
  }

  public void testRegisteredMergeKeepsCompoundFileDecisionOfRegisteringPolicy() throws Exception {
    boolean[] cfs = new boolean[2];
    for (int run = 0; run < 2; run++) {
      try (Directory dir = new ByteBuffersDirectory()) {
        LogDocMergePolicy registering = new LogDocMergePolicy();
        registering.setMergeFactor(2);
        registering.setNoCFSRatio(1.0); // merged segments always use CFS
        OnDemandMergeScheduler scheduler = new OnDemandMergeScheduler();
        IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
        iwc.setCodec(TestUtil.getDefaultCodec());
        iwc.setMergePolicy(registering);
        iwc.setMergeScheduler(scheduler);
        iwc.setMaxBufferedDocs(100);
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          for (int i = 0; i < 2; i++) {
            Document d = new Document();
            d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
            w.addDocument(d);
            w.flush();
          }
          assertTrue(w.hasPendingMerges());
          if (run == 1) {
            LogDocMergePolicy next = new LogDocMergePolicy();
            next.setMergeFactor(1000);
            next.setNoCFSRatio(0.0); // merged segments never use CFS
            w.getConfig().setMergePolicy(next);
          }
          scheduler.runPendingMerges();
          w.commit();
        }
        SegmentInfos infos = SegmentInfos.readLatestCommit(dir);
        assertEquals(1, infos.size());
        cfs[run] = infos.info(0).info.getUseCompoundFile();
      }
    }
    assertTrue(cfs[0]);
    assertEquals(
        "a merge already registered by the previous MergePolicy must not be affected by"
            + " setMergePolicy (compound-file decision of the merged segment)",
        cfs[0],
        cfs[1]);
  }
}
