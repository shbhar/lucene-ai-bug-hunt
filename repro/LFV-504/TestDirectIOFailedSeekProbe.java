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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.mockfile.FilterPath;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A seek to an unaligned position outside the current window whose channel read fails once (the
 * channel stays open). Afterwards, does the next readInt() return the bytes at getFilePointer()?
 * Every file stores at each multiple of 4 its own position as an int. Prints PROBE lines.
 */
public class TestDirectIOFailedSeekProbe extends LuceneTestCase {

  private static final int CAP = DirectIODirectory.DEFAULT_MERGE_BUFFER_SIZE; // 262144

  private static Directory directIO(Path p) throws IOException {
    return new DirectIODirectory(FSDirectory.open(p)) {
      @Override
      protected boolean useDirectIO(String name, IOContext context, OptionalLong fileLength) {
        return fileLength.isPresent();
      }
    };
  }

  public void testReadAfterFailedSeek() throws IOException {
    Path plain = FilterPath.unwrap(createTempDir("seekfail"));
    TestDirectIOStaleWindowAfterFailedRead.FailOnceFS fs =
        new TestDirectIOStaleWindowAfterFailedRead.FailOnceFS(plain.getFileSystem());
    Path p = fs.getFileSystem(URI.create("failonce:///")).getPath(plain.toString());
    final int blockSize = Math.toIntExact(Files.getFileStore(plain).getBlockSize());
    final long target = CAP + 2L * blockSize + 4; // unaligned, outside window 0
    try (Directory d = directIO(p)) {
      try (IndexOutput out = d.createOutput("f", IOContext.DEFAULT)) {
        for (int i = 0; i < 2 * CAP; i += Integer.BYTES) {
          out.writeInt(i);
        }
      }
      try (IndexInput in = d.openInput("f", IOContext.DEFAULT)) {
        in.readBytes(new byte[CAP], 0, CAP); // window 0, read while the device is healthy
        fs.armed = true;
        String seekResult;
        try {
          in.seek(target);
          seekResult = "returned";
        } catch (IOException e) {
          seekResult = "threw IOException";
        }
        assertFalse("the injected failure was not hit", fs.armed);
        final long fp = in.getFilePointer();
        final int v = in.readInt();
        System.out.println(
            "PROBE failedSeek blockSize="
                + blockSize
                + " seek("
                + target
                + ") "
                + seekResult
                + "; then getFilePointer()="
                + fp
                + " readInt()="
                + v
                + " (the int stored at getFilePointer() is "
                + fp
                + ")");
        in.seek(target);
        final int w = in.readInt();
        System.out.println("PROBE failedSeek seek(" + target + ") again: readInt()=" + w);
        assertEquals("seek again: int at " + target, target, w);
        assertEquals("after the failed seek: int at getFilePointer() " + fp, fp, v);
      }
    }
  }
}
