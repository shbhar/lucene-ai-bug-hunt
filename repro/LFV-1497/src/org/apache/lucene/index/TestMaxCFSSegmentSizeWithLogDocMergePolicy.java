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
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestMaxCFSSegmentSizeWithLogDocMergePolicy extends LuceneTestCase {

  // Expected: a merged segment above setMaxCFSSegmentSizeMB(1) is not compound; actual: it is.
  public void testMergedSegmentAboveMaxCFSSegmentSizeIsNotCompound() throws IOException {
    Codec codec = TestUtil.getDefaultCodec();
    codec.compoundFormat().setMaxCFSSegmentSizeMB(1);
    IndexWriterConfig config =
        new IndexWriterConfig()
            .setCodec(codec)
            .setMergePolicy(new LogDocMergePolicy())
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        for (int i = 0; i < 2; i++) {
          Document doc = new Document();
          doc.add(new BinaryDocValuesField("field", new BytesRef(new byte[1024 * 1024])));
          writer.addDocument(doc);
          writer.flush();
        }
        writer.forceMerge(1);
      }
      SegmentInfos infos = SegmentInfos.readLatestCommit(dir);
      assertEquals(1, infos.size());
      SegmentCommitInfo merged = infos.info(0);
      assertTrue(merged.sizeInBytes() > 1024 * 1024);
      assertFalse(
          "a merged segment of " + merged.sizeInBytes() + " bytes is a compound file",
          merged.info.getUseCompoundFile());
    }
  }
}
