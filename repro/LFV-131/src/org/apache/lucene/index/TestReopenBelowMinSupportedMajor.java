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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Version;

public class TestReopenBelowMinSupportedMajor extends LuceneTestCase {

  public void testIsCurrentOnUnchangedIndex() throws Exception {
    int oldMajor = Version.MIN_SUPPORTED_MAJOR - 1;
    try (Directory dir = FSDirectory.open(createTempDir())) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        w.addDocument(new Document());
      }
      SegmentInfo si = SegmentInfos.readLatestCommit(dir).info(0).info;
      si.minVersion = Version.fromBits(oldMajor, 11, 4);
      dir.deleteFile(si.name + ".si");
      si.getCodec().segmentInfoFormat().write(dir, si, IOContext.DEFAULT);

      IndexCommit commit = DirectoryReader.listCommits(dir).get(0);
      try (DirectoryReader reader = DirectoryReader.open(commit, oldMajor, null)) {
        try {
          assertTrue(reader.isCurrent());
        } catch (IndexFormatTooOldException e) {
          throw new AssertionError("expected isCurrent() == true on the unchanged index, got " + e, e);
        }
      }
    }
  }
}
