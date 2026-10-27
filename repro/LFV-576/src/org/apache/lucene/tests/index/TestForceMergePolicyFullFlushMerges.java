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
package org.apache.lucene.tests.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ForceMergePolicy is documented as "A MergePolicy that only returns forced merges" (use it "if
 * you wish to disallow background merges"). It returns null from findMerges but inherits
 * FilterMergePolicy#findFullFlushMerges, which forwards to the wrapped policy, whose default
 * findFullFlushMerges returns the wrapped policy's natural merges. So commit() and NRT reopen still
 * merge segments with the default IndexWriterConfig (maxFullFlushMergeWaitMillis = 500 ms).
 *
 * <p>Public API only.
 */
public class TestForceMergePolicyFullFlushMerges extends LuceneTestCase {

  private static IndexWriterConfig config() {
    TieredMergePolicy tmp = new TieredMergePolicy();
    tmp.setSegmentsPerTier(2); // small index, so that the wrapped policy has natural merges
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setMergePolicy(new ForceMergePolicy(tmp));
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc; // default maxFullFlushMergeWaitMillis
  }

  private static void addDoc(IndexWriter w, int i) throws Exception {
    Document doc = new Document();
    doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
    w.addDocument(doc);
  }

  public void testCommitDoesNotMerge() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      for (int i = 0; i < 6; i++) {
        addDoc(w, i);
        w.commit();
      }
      assertEquals(
          "segments after 6 single-document commits", 6, SegmentInfos.readLatestCommit(dir).size());
    }
  }

  public void testNrtReopenDoesNotMerge() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      for (int i = 0; i < 6; i++) {
        addDoc(w, i);
        w.flush();
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("leaves after 6 single-document flushes", 6, r.leaves().size());
      }
    }
  }

  /** Control: with merge-on-commit switched off, the same commits leave 6 segments. */
  public void testCommitControlWithoutFullFlushMerges() throws Exception {
    IndexWriterConfig iwc = config();
    iwc.setMaxFullFlushMergeWaitMillis(0);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 6; i++) {
        addDoc(w, i);
        w.commit();
      }
      assertEquals(6, SegmentInfos.readLatestCommit(dir).size());
    }
  }
}
