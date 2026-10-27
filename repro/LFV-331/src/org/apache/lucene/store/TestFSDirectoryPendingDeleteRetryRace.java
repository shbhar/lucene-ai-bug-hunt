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
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.tests.mockfile.FilterFileSystemProvider;
import org.apache.lucene.tests.mockfile.WindowsFS;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * FSDirectory retries refused deletes from most mutating operations (maybeDeletePendingFiles ->
 * deletePendingFiles), iterating over a copy of the pending-delete set. If another thread re-creates
 * one of those names in the meantime (createOutput takes the name out of the pending set, deletes
 * the old file and creates a new one), the retry deletes the new file: a file whose createOutput and
 * close succeeded disappears. This is the symptom LUCENE-8318 fixed for the single-threaded case.
 * Only public API is used (no getPendingDeletions).
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestFSDirectoryPendingDeleteRetryRace extends LuceneTestCase {

  /** Pauses the first delete() issued by one chosen thread (to order the two threads). */
  static final class PauseFS extends FilterFileSystemProvider {
    volatile Thread pauseThread;
    final CountDownLatch paused = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);

    PauseFS(FileSystem delegate) {
      super("pause://", delegate);
    }

    @Override
    public void delete(Path path) throws IOException {
      if (Thread.currentThread() == pauseThread) {
        pauseThread = null;
        paused.countDown();
        try {
          if (resume.await(60, TimeUnit.SECONDS) == false) {
            throw new AssertionError("not resumed");
          }
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
      }
      super.delete(path);
    }
  }

  public void testRetryDeletesFileRecreatedConcurrently() throws Exception {
    Path path = createTempDir();
    WindowsFS windows = new WindowsFS(path.getFileSystem()); // open files cannot be deleted
    Path winRoot = windows.wrapPath(path);
    PauseFS pause = new PauseFS(winRoot.getFileSystem());
    List<IndexInput> openReaders = new ArrayList<>();
    try (FSDirectory dir = new NIOFSDirectory(pause.wrapPath(winRoot))) {
      try (IndexOutput out = dir.createOutput("other", IOContext.DEFAULT)) {
        out.writeString("unrelated");
      }
      String[] names = {"x", "f1", "f2", "f3", "f4"};
      for (String name : names) {
        try (IndexOutput out = dir.createOutput(name, IOContext.DEFAULT)) {
          out.writeString("old");
        }
        openReaders.add(dir.openInput(name, IOContext.DEFAULT)); // e.g. files of an open reader
      }
      for (String name : names) {
        dir.deleteFile(name); // the OS refuses (files are open): FSDirectory keeps them pending
      }
      dir.sync(List.of()); // resets FSDirectory's retry counter; the retried deletes still fail
      assertArrayEquals(new String[] {"other"}, dir.listAll());

      openReaders.remove(0).close(); // the reader holding x goes away

      // Thread B does unrelated work: it fsyncs "other". FSDirectory.sync() retries the pending
      // deletes every few calls (maybeDeletePendingFiles); B is paused inside that retry loop.
      Thread b =
          new Thread(
              () -> {
                try {
                  for (int i = 0; i < 1000 && pause.paused.getCount() > 0; i++) {
                    dir.sync(List.of("other"));
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
      pause.pauseThread = b;
      b.start();
      assertTrue(pause.paused.await(60, TimeUnit.SECONDS)); // B is inside its retry loop

      // meanwhile thread C re-creates x
      CountDownLatch created = new CountDownLatch(1);
      Thread c =
          new Thread(
              () -> {
                try (IndexOutput out = dir.createOutput("x", IOContext.DEFAULT)) {
                  out.writeString("new");
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
                created.countDown();
              });
      c.start();
      // On main, C finishes while B is still paused (C needs no lock B holds). An implementation
      // that serializes the two blocks C until B is done; the bounded wait only lets such an
      // implementation make progress, it is not what triggers the failure.
      boolean createdWhileBPaused = created.await(5, TimeUnit.SECONDS);
      pause.resume.countDown();
      b.join();
      c.join();
      if (VERBOSE) {
        System.out.println("x created while the retry loop was paused: " + createdWhileBPaused);
      }

      assertTrue(
          "x was created and closed successfully but is gone: " + Arrays.toString(dir.listAll()),
          Arrays.asList(dir.listAll()).contains("x"));
      assertTrue(Arrays.asList(dir.listAll()).contains("other"));
      try (IndexInput in = dir.openInput("x", IOContext.DEFAULT)) {
        assertEquals("new", in.readString());
      }
    } finally {
      IOUtils.close(openReaders);
    }
  }
}
