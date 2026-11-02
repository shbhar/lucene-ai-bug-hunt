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
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SegmentInfos.FindSegmentsFile calls listAll() twice and retries when the two listings differ,
 * because "listAll() is weakly consistent". A weakly consistent listing (readdir, a
 * ConcurrentHashMap key view) returns every file that exists during the whole call; a file added
 * or removed during the call may or may not be returned. If each of the two listings overlaps one
 * complete commit (rename of pending_segments_N+1 to segments_N+1, then deletion of segments_N), both
 * listings can omit every segments_* file and still be equal, so the check passes and the open
 * throws IndexNotFoundException on a healthy index.
 *
 * <p>The reader-side directory below returns exactly the legal minimal listing: the files present
 * both before and after the call (with write-once file names these are the files present
 * throughout). The commit "during" each listing is a real IndexWriter commit.
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestFindSegmentsFileWeakListing extends LuceneTestCase {

  /** listAll() that overlaps one writer commit for its first {@code listingsWithCommit} calls. */
  private static final class WeakListingDir extends FilterDirectory {
    final IndexWriter writer;
    final AtomicInteger listingsWithCommit;

    WeakListingDir(Directory in, IndexWriter writer, int listingsWithCommit) {
      super(in);
      this.writer = writer;
      this.listingsWithCommit = new AtomicInteger(listingsWithCommit);
    }

    @Override
    public String[] listAll() throws IOException {
      if (listingsWithCommit.getAndDecrement() <= 0) {
        return super.listAll();
      }
      Set<String> atStart = new TreeSet<>(Arrays.asList(super.listAll()));
      // the listing is "in progress" while the writer commits (another thread in practice)
      writer.setLiveCommitData(Map.of("checkpoint", Integer.toString(atStart.size())).entrySet());
      writer.commit();
      Set<String> atEnd = new TreeSet<>(Arrays.asList(super.listAll()));
      atStart.retainAll(atEnd); // only the files that existed during the whole listing
      return atStart.toArray(String[]::new);
    }
  }

  private void openWhileCommitting(Directory base) throws Exception {
    IndexWriter w = new IndexWriter(base, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.YES));
    w.addDocument(doc);
    w.commit(); // segments_1
    WeakListingDir readerDir = new WeakListingDir(base, w, 2);
    // every commit is complete when it becomes visible; the index is never without a commit
    try (DirectoryReader r = DirectoryReader.open(readerDir)) {
      assertEquals(1, r.numDocs());
    } finally {
      w.close();
      base.close();
    }
  }

  public void testByteBuffersDirectory() throws Exception {
    openWhileCommitting(new ByteBuffersDirectory());
  }

  public void testNIOFSDirectory() throws Exception {
    openWhileCommitting(new NIOFSDirectory(createTempDir()));
  }
}
