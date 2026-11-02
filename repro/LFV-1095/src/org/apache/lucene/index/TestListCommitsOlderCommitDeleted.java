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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DirectoryReader.listCommits throws CorruptSegmentInfoException on a healthy index when a commit
 * made while it runs lets the deletion policy remove an older commit after listCommits opened that
 * commit's segments_N but before it read the commit's .si files. The reader-side FilterDirectory
 * only fixes when the commit happens (as another thread would do it); the files are deleted by
 * IndexWriter itself.
 */
public class TestListCommitsOlderCommitDeleted extends LuceneTestCase {

  public void testOlderCommitDeletedWhileListCommitsReadsIt() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      SnapshotDeletionPolicy sdp =
          new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexDeletionPolicy(sdp))) {
        w.addDocument(new Document());
        w.commit(); // segments_1 -> _0
        IndexCommit snapshot = sdp.snapshot(); // e.g. a backup keeps segments_1
        w.addDocument(new Document());
        w.forceMerge(1);
        w.commit(); // segments_2 -> merged segment only; _0 is referenced by segments_1 alone

        boolean[] released = new boolean[1];
        Directory readerDir =
            new FilterDirectory(dir) {
              boolean olderCommitOpened;

              @Override
              public IndexInput openInput(String name, IOContext context) throws IOException {
                if (name.equals("segments_1")) {
                  olderCommitOpened = true;
                } else if (olderCommitOpened && name.endsWith(".si") && released[0] == false) {
                  // the backup finishes and the application commits: the deletion policy now
                  // deletes segments_1 and _0.*
                  released[0] = true;
                  sdp.release(snapshot);
                  w.commit();
                }
                return super.openInput(name, context);
              }
            };

        w.addDocument(new Document()); // pending change for the commit made during listCommits
        // listCommits' javadoc: a commit in progress while it runs may or may not be returned; it
        // must not report the healthy index as corrupt
        List<IndexCommit> commits = DirectoryReader.listCommits(readerDir);
        assertTrue(released[0]);
        assertEquals(2, commits.get(commits.size() - 1).getGeneration());
      }
    }
  }
}
