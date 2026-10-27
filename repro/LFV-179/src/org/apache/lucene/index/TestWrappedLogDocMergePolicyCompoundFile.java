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

/**
 * End to end, public API only (compiles against releases too): forceMerge(1) of 70,000 small
 * documents with LogDocMergePolicy, plain vs wrapped in a FilterMergePolicy. The wrapper must not
 * change whether the merged segment uses the compound file format.
 */
public class TestWrappedLogDocMergePolicyCompoundFile extends LuceneTestCase {

  private static boolean mergedSegmentUsesCfs(MergePolicy mp) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig build =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, build)) {
        for (int s = 0; s < 2; s++) {
          for (int i = 0; i < 35_000; i++) {
            Document doc = new Document();
            doc.add(new StringField("id", s + "-" + i, Field.Store.NO));
            w.addDocument(doc);
          }
          w.commit();
        }
      }
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(mp)
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.forceMerge(1);
        SegmentInfos infos = w.cloneSegmentInfos();
        assertEquals(1, infos.size());
        assertEquals(70_000, infos.info(0).info.maxDoc());
        return infos.info(0).info.getUseCompoundFile();
      }
    }
  }

  public void testFilterMergePolicyKeepsCompoundFileDecision() throws Exception {
    boolean plain = mergedSegmentUsesCfs(new LogDocMergePolicy());
    boolean wrapped = mergedSegmentUsesCfs(new FilterMergePolicy(new LogDocMergePolicy()));
    assertEquals("CFS decision for the same merge, plain vs wrapped policy", plain, wrapped);
  }
}
