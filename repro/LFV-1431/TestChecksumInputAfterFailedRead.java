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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.tests.mockfile.FilterFileChannel;
import org.apache.lucene.tests.mockfile.FilterFileSystemProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestChecksumInputAfterFailedRead extends LuceneTestCase {

  public void testTransientReadFailureIsNotReportedAsCorruption() throws IOException {
    boolean[] failNextRead = new boolean[1];
    Path path = createTempDir();
    FilterFileSystemProvider fs =
        new FilterFileSystemProvider("failonce://", path.getFileSystem()) {
          @Override
          public FileChannel newFileChannel(
              Path p, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
              throws IOException {
            return new FilterFileChannel(super.newFileChannel(p, options, attrs)) {
              @Override
              public int read(ByteBuffer dst, long position) throws IOException {
                if (failNextRead[0]) {
                  failNextRead[0] = false;
                  throw new IOException("transient read failure");
                }
                return super.read(dst, position);
              }
            };
          }
        };
    try (Directory dir = new NIOFSDirectory(fs.wrapPath(path))) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        CodecUtil.writeHeader(out, "test", 0);
        out.writeBytes(new byte[3000], 3000);
        CodecUtil.writeFooter(out);
      }
      try (ChecksumIndexInput in = dir.openChecksumInput("f")) {
        CodecUtil.checkHeader(in, "test", 0, 0);
        failNextRead[0] = true;
        IOException e =
            expectThrows(IOException.class, () -> in.readBytes(new byte[3000], 0, 3000));
        // expected: the IOException is rethrown; actual: CorruptIndexException "checksum failed"
        assertSame(e, expectThrows(Throwable.class, () -> CodecUtil.checkFooter(in, e)));
      }
    }
  }
}
