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
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/** 10.x/9.x counterpart: the same scenario with the settings on the merge policy (setNoCFSRatio). */
public class TestCompoundSettingsAfterReopen10x extends LuceneTestCase {

  private static IndexWriterConfig config(Codec codec) {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(codec);
    LogByteSizeMergePolicy mp = new LogByteSizeMergePolicy();
    mp.setNoCFSRatio(0.0);
    iwc.setUseCompoundFile(false);
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static SegmentCommitInfo onlySegment(Directory dir) throws IOException {
    SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
    assertEquals(1, sis.size());
    return sis.info(0);
  }

  public void testForceMergeOfMergedIndexAfterReopen() throws Exception {
    Codec codec = TestUtil.getDefaultCodec();
    try (Directory dir = newFSDirectory(createTempDir())) {
      try (IndexWriter w = new IndexWriter(dir, config(codec))) {
        for (int i = 0; i < 30; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
          w.addDocument(doc);
          if (i % 10 == 9) {
            w.flush();
          }
        }
        w.forceMerge(1);
        w.commit();
        assertFalse(onlySegment(dir).info.getUseCompoundFile());
      }
      String before = onlySegment(dir).info.name;
      try (IndexWriter w = new IndexWriter(dir, config(codec))) {
        w.forceMerge(1);
        w.commit();
      }
      assertEquals(before, onlySegment(dir).info.name);
    }
  }
}
