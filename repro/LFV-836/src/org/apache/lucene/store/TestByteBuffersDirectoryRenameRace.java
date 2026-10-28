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

import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * ByteBuffersDirectory.rename(source, dest) ends with an unconditional {@code files.remove(source)}
 * after it has already removed the moved entry with {@code files.remove(source, file)}. If another
 * thread creates a new file named {@code source} between those two calls, the second call deletes
 * that new file: its createOutput() succeeded and its output closed normally, nobody deleted or
 * renamed it, yet it is gone from the directory.
 *
 * <p>One thread repeatedly renames "a" away to a fresh name; a second thread re-creates "a" as soon
 * as it is gone and writes a unique id into it. Every id that was written must be found again in
 * some file at the end.
 */
public class TestByteBuffersDirectoryRenameRace extends LuceneTestCase {

  public void testRenameDoesNotDeleteFileCreatedConcurrentlyUnderSourceName() throws Exception {
    final int rounds = 100_000;
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory()) {
      AtomicBoolean stop = new AtomicBoolean();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      CountDownLatch start = new CountDownLatch(1);
      List<Integer> written = new ArrayList<>();

      Thread creator =
          new Thread(
              () -> {
                try {
                  start.await();
                  int id = 0;
                  while (stop.get() == false) {
                    if (dir.fileExists("a")) {
                      Thread.onSpinWait();
                      continue;
                    }
                    try (IndexOutput out = dir.createOutput("a", IOContext.DEFAULT)) {
                      out.writeInt(id);
                      written.add(id); // only this thread touches 'written' until join()
                      id++;
                    } catch (FileAlreadyExistsException _) {
                      // only this thread creates "a", so this cannot happen; keep going
                    }
                  }
                } catch (Throwable t) {
                  failure.set(t);
                }
              });
      creator.start();
      start.countDown();

      for (int i = 0; i < rounds && failure.get() == null; i++) {
        while (dir.fileExists("a") == false && failure.get() == null) {
          Thread.onSpinWait();
        }
        // "a" exists and only this thread removes it, so the rename must succeed
        dir.rename("a", "b" + i);
      }
      stop.set(true);
      creator.join();
      if (failure.get() != null) {
        throw new AssertionError(failure.get());
      }

      // Every id that the creator wrote (createOutput + close returned normally) must still be in
      // some file: either renamed to a "b<i>" by this thread, or still named "a".
      Set<Integer> found = new TreeSet<>();
      for (String name : dir.listAll()) {
        try (IndexInput in = dir.openInput(name, IOContext.DEFAULT)) {
          found.add(in.readInt());
        }
      }
      Set<Integer> lost = new TreeSet<>(written);
      lost.removeAll(found);
      assertTrue(
          "files created with createOutput(\"a\") and closed normally, never deleted or renamed, "
              + "are missing from the directory: "
              + lost.size()
              + " of "
              + written.size()
              + " (first ids "
              + lost.stream().limit(5).toList()
              + ")",
          lost.isEmpty());
    }
  }
  /**
   * Same root cause, second manifestation: a producer publishes files under the fixed name "a" by
   * writing "t<i>" and renaming it to "a"; a consumer renames "a" away to "b<i>". A producer rename
   * that lands between the consumer's two removes is deleted by the consumer's trailing remove.
   */
  public void testRenameDoesNotDeleteFileRenamedConcurrentlyOntoSourceName() throws Exception {
    final int rounds = 100_000;
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory()) {
      AtomicBoolean stop = new AtomicBoolean();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      CountDownLatch start = new CountDownLatch(1);
      List<Integer> published = new ArrayList<>();

      Thread producer =
          new Thread(
              () -> {
                try {
                  start.await();
                  int id = 0;
                  while (stop.get() == false) {
                    String tmp = "t" + id;
                    try (IndexOutput out = dir.createOutput(tmp, IOContext.DEFAULT)) {
                      out.writeInt(id);
                    }
                    while (dir.fileExists("a") && stop.get() == false) {
                      Thread.onSpinWait();
                    }
                    if (stop.get()) {
                      break;
                    }
                    // "a" is absent and only this thread creates it, so the rename must succeed
                    dir.rename(tmp, "a");
                    published.add(id);
                    id++;
                  }
                } catch (Throwable t) {
                  failure.set(t);
                }
              });
      producer.start();
      start.countDown();

      for (int i = 0; i < rounds && failure.get() == null; i++) {
        while (dir.fileExists("a") == false && failure.get() == null) {
          Thread.onSpinWait();
        }
        dir.rename("a", "b" + i);
      }
      stop.set(true);
      producer.join();
      if (failure.get() != null) {
        throw new AssertionError(failure.get());
      }

      Set<Integer> found = new TreeSet<>();
      for (String name : dir.listAll()) {
        if (name.startsWith("t")) {
          continue; // the last, unpublished temp file
        }
        try (IndexInput in = dir.openInput(name, IOContext.DEFAULT)) {
          found.add(in.readInt());
        }
      }
      Set<Integer> lost = new TreeSet<>(published);
      lost.removeAll(found);
      assertTrue(
          "files published with rename(t, \"a\") that returned normally are missing from the "
              + "directory: "
              + lost.size()
              + " of "
              + published.size()
              + " (first ids "
              + lost.stream().limit(5).toList()
              + ")",
          lost.isEmpty());
    }
  }
}
