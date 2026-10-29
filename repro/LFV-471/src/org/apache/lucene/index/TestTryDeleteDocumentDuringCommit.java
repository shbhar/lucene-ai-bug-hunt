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
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

public class TestTryDeleteDocumentDuringCommit extends LuceneTestCase {

  private volatile Runnable duringCommitFlush;
  private Thread deleter;

  public void testSeqNoAboveCommitButDeleteInCommit() throws Exception {
    Thread committer = Thread.currentThread();
    Directory dir =
        new FilterDirectory(newDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            Runnable r = duringCommitFlush;
            if (r != null && Thread.currentThread() == committer) {
              duringCommitFlush = null;
              deleter = new Thread(r);
              deleter.start();
              try {
                deleter.join(2000);
              } catch (InterruptedException e) {
                throw new ThreadInterruptedException(e);
              }
            }
            return super.createOutput(name, context);
          }
        };
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    w.addDocument(new Document());
    DirectoryReader nrt = DirectoryReader.open(w);
    w.addDocument(new Document());
    AtomicLong deleteSeqNo = new AtomicLong();
    duringCommitFlush =
        () -> {
          try {
            deleteSeqNo.set(w.tryDeleteDocument(nrt, 0));
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        };
    long commitSeqNo = w.commit();
    deleter.join();
    boolean deleteInCommit;
    try (DirectoryReader committed = DirectoryReader.open(dir)) {
      deleteInCommit = committed.numDocs() == 1;
    }
    nrt.close();
    w.close();
    dir.close();

    // commit(): "All sequence numbers <= this value will be reflected in the commit, and all others will not"
    assertEquals(
        "tryDeleteDocument seqNo=" + deleteSeqNo + ", commit seqNo=" + commitSeqNo + ", in commit",
        deleteSeqNo.get() <= commitSeqNo,
        deleteInCommit);
  }
}
