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

public class TestOrphanedNrtReaderIsCurrent extends LuceneTestCase {

  public void testIsCurrentAfterRollbackAndNewWriterCommits() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, new IndexWriterConfig());
      w1.addDocument(new Document());
      w1.commit();
      DirectoryReader reader = DirectoryReader.open(w1);
      for (int i = 0; i < 3; i++) {
        w1.addDocument(new Document());
        DirectoryReader newReader = DirectoryReader.openIfChanged(reader, w1);
        reader.close();
        reader = newReader;
      }
      w1.rollback();
      try (IndexWriter w2 = new IndexWriter(dir, new IndexWriterConfig())) {
        w2.addDocument(new Document());
        w2.commit();
        w2.addDocument(new Document());
        w2.commit();
      }
      boolean current = reader.isCurrent();
      String state =
          "reader: maxDoc "
              + reader.maxDoc()
              + ", version "
              + reader.getVersion()
              + "; latest commit: maxDoc "
              + SegmentInfos.readLatestCommit(dir).totalMaxDoc()
              + ", version "
              + SegmentInfos.readLatestCommit(dir).getVersion();
      reader.close();
      // expected false (a new writer committed different content); actual true (same version)
      assertFalse("isCurrent() expected false, was true; " + state, current);
    }
  }
}
