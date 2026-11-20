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

import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestForceMergeTwiceAtDefaultCompoundFileThreshold extends LuceneTestCase {

  // Expected: the second forceMerge(1) leaves the one merged segment alone. Actual: it merges it.
  public void testSecondForceMergeLeavesTheMergedSegmentAlone() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(new LogByteSizeMergePolicy());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Random r = new Random(17036);
      for (int i = 0; i < 64; i++) {
        byte[] blob = new byte[i < 63 ? 1 << 20 : 749_624];
        r.nextBytes(blob);
        Document doc = new Document();
        doc.add(new StoredField("blob", blob));
        w.addDocument(doc);
        if (i == 31) {
          w.commit();
        }
      }
      w.forceMerge(1);
      w.commit();
      SegmentInfos infos = SegmentInfos.readLatestCommit(dir);
      assertEquals(1, infos.size());
      SegmentCommitInfo merged = infos.info(0);
      String mergedName = merged.info.name;
      String mergedState =
          merged.sizeInBytes()
              + " bytes, "
              + (merged.info.getUseCompoundFile() ? "compound" : "not compound");

      w.forceMerge(1);
      w.commit();

      assertEquals(
          "the second forceMerge(1) wrote the one merged segment again (it was "
              + mergedName
              + ": "
              + mergedState
              + ")",
          mergedName,
          SegmentInfos.readLatestCommit(dir).info(0).info.name);
    }
  }
}
