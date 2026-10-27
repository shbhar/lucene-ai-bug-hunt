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
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An NRT reader whose writer ended without committing (rollback(), or close() with
 * commitOnClose=false) must report isCurrent() == false once a later writer has committed new
 * content to the same directory.
 *
 * <p>DirectoryReader#isCurrent javadoc: for a near real-time reader "this method checks if either a
 * new commit has occurred, or any new uncommitted changes have taken place via the writer".
 *
 * <p>Uses public API only. The writer configs are fixed (no merges, no randomized config), so the
 * sequence of SegmentInfos versions is the same on every run.
 */
public class TestOrphanedNrtReaderIsCurrent extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static IndexWriterConfig config(boolean commitOnClose) {
    return new IndexWriterConfig()
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setCommitOnClose(commitOnClose);
  }

  private void checkOrphanedReader(Directory dir, boolean useRollback) throws IOException {
    // Writer 1: one commit ("A"), then three uncommitted documents, each made visible through NRT.
    IndexWriter w1 = new IndexWriter(dir, config(useRollback));
    w1.addDocument(doc("A"));
    w1.commit();
    DirectoryReader reader = DirectoryReader.open(w1);
    for (int i = 1; i <= 3; i++) {
      w1.addDocument(doc("W1-" + i));
      DirectoryReader newReader = DirectoryReader.openIfChanged(reader, w1);
      assertNotNull(newReader);
      reader.close();
      reader = newReader;
    }
    // Writer 1 ends without committing W1-1..W1-3.
    if (useRollback) {
      w1.rollback();
    } else {
      w1.close(); // commitOnClose=false: close() rolls back
    }
    final DirectoryReader orphan = reader;
    // Documented: once the writer is closed, the reader can still be used but not reopened.
    expectThrows(AlreadyClosedException.class, () -> DirectoryReader.openIfChanged(orphan));

    // Writer 2: a new session on the same directory commits different content.
    try (IndexWriter w2 = new IndexWriter(dir, config(true))) {
      for (int i = 1; i <= 4; i++) {
        w2.addDocument(doc("W2-" + i));
        w2.commit();
        SegmentInfos latest = SegmentInfos.readLatestCommit(dir);
        String state =
            String.format(
                Locale.ROOT,
                "after writer-2 commit #%d: latest commit %s version=%d maxDoc=%d;"
                    + " orphaned NRT reader version=%d maxDoc=%d (3 of them rolled back)",
                i,
                latest.getSegmentsFileName(),
                latest.getVersion(),
                latest.totalMaxDoc(),
                orphan.getVersion(),
                orphan.maxDoc());
        System.out.println(state + " isCurrent()=" + orphan.isCurrent());
        assertFalse(
            "isCurrent() must be false once a new commit has occurred, " + state,
            orphan.isCurrent());
      }
    } finally {
      orphan.close();
    }
  }

  public void testRollback() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      checkOrphanedReader(dir, true);
    }
  }

  public void testCloseWithoutCommit() throws IOException {
    try (Directory dir = FSDirectory.open(createTempDir("orphan-nrt"))) {
      checkOrphanedReader(dir, false);
    }
  }
}
