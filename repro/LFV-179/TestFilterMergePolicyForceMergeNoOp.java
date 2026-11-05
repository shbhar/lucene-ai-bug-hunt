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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFilterMergePolicyForceMergeNoOp extends LuceneTestCase {

  /** Wrapping LogDocMergePolicy must not make forceMerge(1) rewrite an already merged segment. */
  public void testSecondForceMergeIsNoOp() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(new FilterMergePolicy(new LogDocMergePolicy()))
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      // 70,000 docs: above the 65,536-doc compound-file threshold, far below 64 MB
      for (int i = 0; i < 70_000; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      SegmentInfo first = w.cloneSegmentInfos().info(0).info;
      w.forceMerge(1); // one segment, no deletions: nothing to do
      SegmentInfo second = w.cloneSegmentInfos().info(0).info;
      assertEquals(
          "second forceMerge(1) rewrote "
              + first.name
              + " (compound file: "
              + first.getUseCompoundFile()
              + ")",
          first.name,
          second.name);
    }
  }
}
