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
package org.apache.lucene.misc.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MultiPassIndexSplitter.split opens an IndexWriter on each output directory and closes it only
 * after addIndexes succeeded. If addIndexes throws (here an I/O error writing the output, e.g. a
 * full disk), the writer is never closed: the output directory stays write-locked and its files
 * stay open.
 */
public class TestMultiPassIndexSplitterFailure extends LuceneTestCase {

  private static boolean onStack(String classSimpleName, String method) {
    for (StackTraceElement e : new Throwable().getStackTrace()) {
      String c = e.getClassName();
      if (c.substring(c.lastIndexOf('.') + 1).equals(classSimpleName) && e.getMethodName().equals(method)) {
        return true;
      }
    }
    return false;
  }

  public void testFailedSplitReleasesOutputDirectory() throws Exception {
    try (Directory input = newDirectory();
        MockDirectoryWrapper out1 = newMockFSDirectory(createTempDir("out1"));
        MockDirectoryWrapper out2 = newMockFSDirectory(createTempDir("out2"))) {
      try (IndexWriter w = new IndexWriter(input, new IndexWriterConfig(null))) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", "" + i, Field.Store.YES));
          w.addDocument(doc);
        }
      }
      out1.failOn(
          new MockDirectoryWrapper.Failure() {
            boolean done;

            @Override
            public void eval(MockDirectoryWrapper dir) throws IOException {
              // the first file split() writes into this fresh output is written by addIndexes
              if (done == false && onStack("MockDirectoryWrapper", "createOutput")) {
                done = true;
                throw new IOException("simulated disk full on the output directory");
              }
            }
          });
      try (DirectoryReader reader = DirectoryReader.open(input)) {
        // split() uses a ConcurrentMergeScheduler, whose merge thread also rethrows the merge
        // failure as an uncaught exception (its normal behaviour). Run split() in a thread group
        // that absorbs that expected report, so only the leak is reported below.
        Throwable[] thrown = new Throwable[1];
        ThreadGroup group =
            new ThreadGroup("split") {
              @Override
              public void uncaughtException(Thread t, Throwable e) {}
            };
        Thread splitter =
            new Thread(
                group,
                () -> {
                  try {
                    new MultiPassIndexSplitter().split(reader, new Directory[] {out1, out2}, false);
                  } catch (Throwable t) {
                    thrown[0] = t;
                  }
                });
        splitter.start();
        splitter.join();
        assertTrue(String.valueOf(thrown[0]), thrown[0] instanceof IOException);
        assertEquals("simulated disk full on the output directory", thrown[0].getMessage());
      }
      LockObtainFailedException leaked = null;
      try {
        new IndexWriter(out1, new IndexWriterConfig(null)).close();
      } catch (LockObtainFailedException lofe) {
        leaked = lofe;
      }
      assertNull("split() failed and left an IndexWriter open on the output: " + leaked, leaked);
    }
  }
}
