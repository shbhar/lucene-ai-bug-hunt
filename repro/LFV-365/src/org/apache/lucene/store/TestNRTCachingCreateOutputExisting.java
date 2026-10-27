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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Directory#createOutput "must throw FileAlreadyExistsException if the file already exists"
 * (Directory class javadoc and createOutput javadoc). NRTCachingDirectory#createOutput only asks
 * the home that the IOContext selects (the RAM cache for small flushes/merges, the wrapped
 * directory otherwise), so a name that already exists in the other home is created a second time.
 * The file then lives in both homes: deleteFile removes only one copy, and close() loses the file.
 * (FilterDirectory#copyFrom routes through createOutput, but its own javadoc requires a
 * non-existent dest, so that entry point is not tested here.)
 *
 * <p>Public API only. Delegate: FSDirectory, as in the NRTCachingDirectory class javadoc example.
 */
public class TestNRTCachingCreateOutputExisting extends LuceneTestCase {

  /** A small flush: NRTCachingDirectory#doCacheWrite returns true, the file goes to the cache. */
  private static final IOContext CACHED = IOContext.flush(new FlushInfo(1, 100));

  private static void write(Directory dir, String name, IOContext ctx, int value)
      throws IOException {
    try (IndexOutput out = dir.createOutput(name, ctx)) {
      out.writeInt(value);
    }
  }

  private static int read(Directory dir, String name) throws IOException {
    try (IndexInput in = dir.openInput(name, IOContext.READONCE)) {
      return in.readInt();
    }
  }

  /** The name exists in the wrapped directory; createOutput with a cached IOContext. */
  public void testCreateOutputExistingInDelegate() throws IOException {
    Path path = createTempDir();
    NRTCachingDirectory dir = new NRTCachingDirectory(FSDirectory.open(path), 1.0, 10.0);
    try {
      write(dir, "_0.si", IOContext.DEFAULT, 1); // IOContext.DEFAULT is never cached
      assertEquals(0, dir.listCachedFiles().length);
      expectThrows(FileAlreadyExistsException.class, () -> write(dir, "_0.si", CACHED, 2));
    } finally {
      closeQuietly(dir);
    }
  }

  /** The name exists in the RAM cache; createOutput with IOContext.DEFAULT (wrapped directory). */
  public void testCreateOutputExistingInCache() throws IOException {
    Path path = createTempDir();
    NRTCachingDirectory dir = new NRTCachingDirectory(FSDirectory.open(path), 1.0, 10.0);
    try {
      write(dir, "_0.si", CACHED, 1);
      assertEquals(List.of("_0.si"), Arrays.asList(dir.listCachedFiles()));
      expectThrows(
          FileAlreadyExistsException.class, () -> write(dir, "_0.si", IOContext.DEFAULT, 2));
    } finally {
      closeQuietly(dir);
    }
  }

  /**
   * Consequence: deleteFile("_0.si") returns normally, but the file is still listed and still
   * readable, with the content of the first write.
   */
  public void testDeleteFileAfterSecondCreate() throws IOException {
    Path path = createTempDir();
    NRTCachingDirectory dir = new NRTCachingDirectory(FSDirectory.open(path), 1.0, 10.0);
    try {
      write(dir, "_0.si", IOContext.DEFAULT, 1);
      try {
        write(dir, "_0.si", CACHED, 2);
      } catch (FileAlreadyExistsException expected) {
        return; // the documented behaviour; nothing else to check
      }
      dir.deleteFile("_0.si");
      if (Arrays.asList(dir.listAll()).contains("_0.si")) {
        fail("deleted file is still listed and reads back " + read(dir, "_0.si"));
      }
    } finally {
      closeQuietly(dir);
    }
  }

  /**
   * Consequence: NRTCachingDirectory#close() "flushes any cached files to the delegate". After the
   * second create, close() deletes the on-disk copy (the failure cleanup of the internal copy) and
   * drops the cached copy, so the file is gone (with assertions disabled).
   */
  public void testCloseAfterSecondCreateKeepsFile() throws IOException {
    Path path = createTempDir();
    NRTCachingDirectory dir = new NRTCachingDirectory(FSDirectory.open(path), 1.0, 10.0);
    boolean secondCreateFailed = false;
    try {
      write(dir, "_0.si", IOContext.DEFAULT, 1);
      try {
        write(dir, "_0.si", CACHED, 2);
      } catch (FileAlreadyExistsException expected) {
        secondCreateFailed = true; // the documented behaviour
      }
    } finally {
      Throwable closeFailure = null;
      try {
        dir.close();
      } catch (Throwable t) {
        closeFailure = t;
      }
      if (VERBOSE && closeFailure != null) {
        closeFailure.printStackTrace(System.out);
      }
    }
    try (Directory fs = FSDirectory.open(path)) {
      assertTrue(
          "file lost by NRTCachingDirectory.close() (secondCreateFailed="
              + secondCreateFailed
              + ", on disk: "
              + Arrays.toString(fs.listAll())
              + ")",
          Arrays.asList(fs.listAll()).contains("_0.si"));
    }
  }

  private static void closeQuietly(Directory dir) {
    try {
      dir.close();
    } catch (Throwable _) {
      // close() itself fails once a name lives in both homes; the assertion above is the finding
    }
  }
}
