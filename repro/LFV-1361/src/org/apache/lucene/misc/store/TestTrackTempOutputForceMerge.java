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
package org.apache.lucene.misc.store;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestTrackTempOutputForceMerge extends LuceneTestCase {

  // Expected: forceMerge(1) succeeds; actual: NoSuchFileException "ByteTrackingIndexOutput{_..._4.tmp}".
  public void testForceMergeWithTrackedTempOutputs() throws Exception {
    try (ByteWritesTrackingDirectoryWrapper dir =
        new ByteWritesTrackingDirectoryWrapper(new ByteBuffersDirectory(), true)) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(new Document());
        w.commit();
        w.addDocument(new Document());
        w.commit();
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
      }
    }
  }
}
