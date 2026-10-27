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
package org.apache.lucene.facet.taxonomy.directory;

import java.io.IOException;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DirectoryTaxonomyWriter.close() is "commit(); indexWriter.close(); doClose();" with no
 * try/finally. When commit() throws, close() throws before it closes the IndexWriter and its own
 * NRT reader manager, so the taxonomy's files (and, if the IndexWriter is still open, its write
 * lock) are never released. Its javadoc says close "Frees used resources as well as closes the
 * underlying IndexWriter".
 */
public class TestDirectoryTaxonomyWriterCloseAfterFailure extends LuceneTestCase {

  private static boolean onStack(String classSimpleName, String method) {
    for (StackTraceElement e : new Throwable().getStackTrace()) {
      String c = e.getClassName();
      if (c.substring(c.lastIndexOf('.') + 1).equals(classSimpleName) && e.getMethodName().equals(method)) {
        return true;
      }
    }
    return false;
  }

  private static MockDirectoryWrapper.Failure failOnceIn(String phase, String op) {
    return new MockDirectoryWrapper.Failure() {
      boolean done;

      @Override
      public void eval(MockDirectoryWrapper dir) throws IOException {
        if (done == false
            && onStack("DirectoryTaxonomyWriter", phase)
            && onStack("MockDirectoryWrapper", op)) {
          done = true;
          throw new IOException("simulated I/O error in " + op + " during " + phase);
        }
      }
    };
  }

  /**
   * An I/O error while commit() writes the new segment closes the IndexWriter (tragic event). The
   * application then closes the taxonomy writer, as it must; close() throws AlreadyClosedException
   * from its commit() and never closes the reader manager, whose reader keeps a segment open.
   */
  public void testCloseAfterFailedCommitReleasesFiles() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir);
    tw.addCategory(new FacetLabel("dim", "a"));
    tw.commit();
    tw.addCategory(new FacetLabel("dim", "b"));
    dir.failOn(failOnceIn("commit", "createOutput"));
    expectThrows(IOException.class, tw::commit);
    expectThrows(AlreadyClosedException.class, tw::close);
    // closing the directory checks that nothing is left open
    dir.close();
  }

  /**
   * An I/O error while close() syncs its final commit does not close the IndexWriter, and close()
   * throws before closing it, so the taxonomy stays write-locked.
   */
  public void testCloseFailureReleasesWriteLock() throws Exception {
    try (MockDirectoryWrapper dir = newMockFSDirectory(createTempDir("taxo"))) {
      DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir);
      tw.addCategory(new FacetLabel("dim", "a"));
      dir.failOn(failOnceIn("close", "sync"));
      IOException e = expectThrows(IOException.class, tw::close);
      assertEquals("simulated I/O error in sync during close", e.getMessage());
      LockObtainFailedException leaked = null;
      try {
        new DirectoryTaxonomyWriter(dir).close();
      } catch (LockObtainFailedException lofe) {
        leaked = lofe;
      }
      assertNull("close() failed and left the taxonomy write-locked: " + leaked, leaked);
    }
  }
}
