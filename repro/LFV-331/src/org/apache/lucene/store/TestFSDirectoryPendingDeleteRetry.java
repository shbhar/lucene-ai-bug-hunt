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
package org.apache.lucene.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.tests.mockfile.FilterFileSystemProvider;
import org.apache.lucene.tests.mockfile.WindowsFS;
import org.apache.lucene.tests.util.LuceneTestCase;

@LuceneTestCase.SuppressFileSystems("*")
public class TestFSDirectoryPendingDeleteRetry extends LuceneTestCase {

  public void testRetryKeepsFileRecreatedByAnotherThread() throws Exception {
    Path tmp = createTempDir();
    Path windows = new WindowsFS(tmp.getFileSystem()).wrapPath(tmp);
    AtomicReference<Thread> startOnNextDelete = new AtomicReference<>();
    Path root =
        new FilterFileSystemProvider("hook://", windows.getFileSystem()) {
          @Override
          public void delete(Path path) throws IOException {
            Thread t = startOnNextDelete.getAndSet(null);
            if (t != null) {
              t.start();
              while (t.getState() != Thread.State.TERMINATED
                  && t.getState() != Thread.State.BLOCKED) {
                Thread.onSpinWait();
              }
            }
            super.delete(path);
          }
        }.wrapPath(windows);
    try (FSDirectory dir = new NIOFSDirectory(root)) {
      dir.createOutput("x", IOContext.DEFAULT).close();
      dir.createOutput("y", IOContext.DEFAULT).close();
      IndexInput inX = dir.openInput("x", IOContext.DEFAULT);
      IndexInput inY = dir.openInput("y", IOContext.DEFAULT);
      dir.deleteFile("x");
      dir.deleteFile("y");
      inX.close();
      Thread recreateX =
          new Thread(
              () -> {
                try {
                  dir.createOutput("x", IOContext.DEFAULT).close();
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      startOnNextDelete.set(recreateX);
      dir.syncMetaData();
      recreateX.join();
      // expected: x, re-created by the other thread, still exists; actual: the retry deleted it
      assertTrue(
          "expected x in listAll() but was " + Arrays.toString(dir.listAll()),
          Arrays.asList(dir.listAll()).contains("x"));
      inY.close();
    }
  }
}
