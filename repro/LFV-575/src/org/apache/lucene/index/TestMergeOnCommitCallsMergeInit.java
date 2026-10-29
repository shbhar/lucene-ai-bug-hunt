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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMergeOnCommitCallsMergeInit extends LuceneTestCase {

  public void testMergeOnCommitCallsMergeInit() throws Exception {
    int[] mergeInit = new int[1];
    int[] mergeFinished = new int[1];
    MergePolicy mp =
        new FilterMergePolicy(new LogDocMergePolicy()) {
          @Override
          public MergeSpecification findFullFlushMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
            if (infos.size() < 2) {
              return null;
            }
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(infos.asList()) {
                  @Override
                  public void mergeInit() throws IOException {
                    mergeInit[0]++;
                    super.mergeInit();
                  }

                  @Override
                  public void mergeFinished(boolean success, boolean segmentDropped)
                      throws IOException {
                    mergeFinished[0]++;
                    super.mergeFinished(success, segmentDropped);
                  }
                });
            return spec;
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig().setMergePolicy(mp).setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(new Document());
      w.commit();
      w.addDocument(new Document());
      w.commit(); // merges the two segments on commit
      assertEquals(1, SegmentInfos.readLatestCommit(dir).size());
      assertEquals(1, mergeFinished[0]);
      assertEquals("mergeInit calls before mergeFinished", 1, mergeInit[0]);
    }
  }
}
