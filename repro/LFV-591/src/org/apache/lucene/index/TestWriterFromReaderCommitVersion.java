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

import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestWriterFromReaderCommitVersion extends LuceneTestCase {

  private static IndexWriterConfig keepAllCommits() {
    return new IndexWriterConfig().setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE);
  }

  public void testWriterFromCommitReader() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w1 = new IndexWriter(dir, keepAllCommits());
    w1.addDocument(new Document());
    w1.commit();
    DirectoryReader older = DirectoryReader.open(dir);
    w1.addDocument(new Document());
    w1.commit();
    w1.addDocument(new Document());
    w1.commit();
    w1.close();
    DirectoryReader newer = DirectoryReader.open(dir);

    IndexWriter w2 = new IndexWriter(dir, keepAllCommits().setIndexCommit(older.getIndexCommit()));
    w2.setLiveCommitData(Map.of("step", "1").entrySet());
    w2.commit();
    w2.setLiveCommitData(Map.of("step", "2").entrySet());
    w2.commit();
    // expected false (segments_5, 1 doc, came after the reader's segments_3, 3 docs); actual true
    assertFalse(
        "isCurrent() expected false but was true; reader version "
            + newer.getVersion()
            + ", latest commit version "
            + SegmentInfos.readLatestCommit(dir).getVersion(),
        newer.isCurrent());

    w2.close();
    older.close();
    newer.close();
    dir.close();
  }
}
