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
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Probe, not a repro: prints what the reference (FSDirectory.open) and DirectIODirectory report
 * after a read that threw. Each file stores its own position as an int at every multiple of 4.
 * Every PROBE line names the scenario, the directory and what it saw; the test fails if they differ.
 */
public class TestDirectIOFailedReadProbe extends LuceneTestCase {

  private static final int CAP = DirectIODirectory.DEFAULT_MERGE_BUFFER_SIZE;

  private static void writeFile(Directory dir, String name, int n) throws IOException {
    try (IndexOutput out = dir.createOutput(name, IOContext.DEFAULT)) {
      for (int p = 0; p < n; p += Integer.BYTES) {
        out.writeInt(p);
      }
    }
  }

  private static Directory directIO(Path p) throws IOException {
    return new DirectIODirectory(FSDirectory.open(p)) {
      @Override
      protected boolean useDirectIO(String name, IOContext context, OptionalLong fileLength) {
        return fileLength.isPresent();
      }
    };
  }

  private interface Scenario {
    String run(IndexInput in) throws IOException;
  }

  private static String readIntOrEx(IndexInput in) {
    long fp = in.getFilePointer();
    try {
      return "fp=" + fp + " readInt=" + in.readInt();
    } catch (IOException e) {
      return "fp=" + fp + " readInt=" + e.getClass().getSimpleName();
    }
  }

  private void compare(String name, int n, Scenario s) throws IOException {
    Path p = createTempDir(name);
    String ref, dio;
    try (Directory fs = FSDirectory.open(p)) {
      writeFile(fs, "f", n);
      try (IndexInput in = fs.openInput("f", IOContext.DEFAULT)) {
        ref = s.run(in);
      }
    }
    try (Directory d = directIO(p);
        IndexInput in = d.openInput("f", IOContext.DEFAULT)) {
      assertTrue(in.toString().startsWith("DirectIOIndexInput"));
      dio = s.run(in);
    }
    System.out.println("PROBE " + name + " reference: " + ref);
    System.out.println("PROBE " + name + " DirectIO:  " + dio);
    assertEquals(name, ref, dio);
  }

  /** [B, no seek] An oversized first read throws; then two reads without any seek. */
  public void testNextReadsAfterOversizedFirstRead() throws IOException {
    compare(
        "oversizedThenRead",
        2 * CAP + 16,
        in -> {
          expectThrows(EOFException.class, () -> in.readBytes(new byte[3 * CAP], 0, 3 * CAP));
          return readIntOrEx(in) + "; " + readIntOrEx(in);
        });
  }

  /** [D, no seek] After one full window, a read past EOF throws; the file pointer, then a read. */
  public void testFilePointerAfterReadPastEOF() throws IOException {
    compare(
        "readPastEOFThenRead",
        CAP + 96,
        in -> {
          in.readBytes(new byte[CAP], 0, CAP);
          expectThrows(EOFException.class, () -> in.readBytes(new byte[200], 0, 200));
          return "length=" + in.length() + " " + readIntOrEx(in);
        });
  }

  /** [D] After the read past EOF, a legal seek, then three ints across the window. */
  public void testIntsAfterLegalSeek() throws IOException {
    compare(
        "readPastEOFThenSeek",
        CAP + 96,
        in -> {
          in.readBytes(new byte[CAP], 0, CAP);
          expectThrows(EOFException.class, () -> in.readBytes(new byte[200], 0, 200));
          in.seek(CAP + 4);
          String a = readIntOrEx(in);
          in.seek(CAP + 92);
          String b = readIntOrEx(in);
          return a + "; " + b;
        });
  }
  /**
   * Seek past the end of a short slice whose end lies in its first block, then ask for the file
   * pointer and read. Prints only (the state after a seek past EOF is undetermined by contract);
   * an AssertionError from getFilePointer() under -ea is printed as such.
   */
  public void testSeekPastEndOfShortSlice() throws IOException {
    Path p = createTempDir("shortSlice");
    try (Directory d = directIO(p)) {
      writeFile(d, "f", 200);
      try (IndexInput in = d.openInput("f", IOContext.DEFAULT)) {
        IndexInput s = in.slice("s", 100, 4);
        expectThrows(EOFException.class, () -> s.seek(150));
        String fp;
        try {
          fp = "fp=" + s.getFilePointer();
        } catch (AssertionError e) {
          fp = "fp=AssertionError";
        }
        String next;
        try {
          next = "readByte=" + s.readByte();
        } catch (IOException e) {
          next = "readByte=" + e.getClass().getSimpleName();
        }
        System.out.println("PROBE seekPastShortSliceEnd DirectIO: " + fp + " " + next);
      }
    }
  }
}
