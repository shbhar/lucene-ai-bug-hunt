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
package org.apache.lucene.misc.store;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.BeforeClass;

/**
 * IndexInput.seek: "If this is beyond the end of the file then this will throw EOFException".
 * DirectIOIndexInput computes pos + offset without an overflow check, so on a slice that starts at
 * a non-zero offset a target above Long.MAX_VALUE - offset wraps to a negative absolute position
 * and the read underneath throws IllegalArgumentException("Negative position") instead.
 */
public class TestDirectIOSliceSeekOverflow extends LuceneTestCase {

  @BeforeClass
  public static void checkSupported() throws IOException {
    assumeTrue(
        "needs a JDK with ExtendedOpenOption.DIRECT",
        DirectIODirectory.ExtendedOpenOption_DIRECT != null);
    Path path = createTempDir("directIOProbe");
    try (Directory dir = directIO(path);
        IndexOutput out = dir.createOutput("out", IOContext.DEFAULT)) {
      out.writeString("test");
    } catch (IOException e) {
      assumeNoException("needs a file system that supports direct I/O", e);
    }
  }

  private static Directory directIO(Path path) throws IOException {
    return new DirectIODirectory(FSDirectory.open(path)) {
      @Override
      protected boolean useDirectIO(String name, IOContext context, OptionalLong fileLength) {
        return true;
      }
    };
  }

  private static void writeFile(Directory dir) throws IOException {
    try (IndexOutput out = dir.createOutput("file", IOContext.DEFAULT)) {
      out.writeBytes(new byte[20_000], 0, 20_000);
    }
  }

  private static void checkHugeSeeksThrowEOF(Directory dir) throws IOException {
    try (IndexInput in = dir.openInput("file", IOContext.DEFAULT)) {
      IndexInput slice = in.slice("slice", 100, 1_000);
      expectThrows(EOFException.class, () -> slice.seek(Long.MAX_VALUE));
      IndexInput slice2 = in.slice("slice2", 100, 1_000);
      expectThrows(EOFException.class, () -> slice2.skipBytes(Long.MAX_VALUE - 10));
    }
  }

  public void testHugeSeekOnSliceThrowsEOF() throws Exception {
    Path path = createTempDir("directIO");
    try (Directory dir = directIO(path)) {
      writeFile(dir);
      checkHugeSeeksThrowEOF(dir);
    }
  }

  /** Control: the same calls on NIOFSDirectory throw EOFException. */
  public void testControlNIOFSDirectory() throws Exception {
    Path path = createTempDir("niofs");
    try (Directory dir = new NIOFSDirectory(path)) {
      writeFile(dir);
      checkHugeSeeksThrowEOF(dir);
    }
  }
}
