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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.mockfile.FilterFileChannel;
import org.apache.lucene.tests.mockfile.FilterFileSystemProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDirectIOOutputShortWrite extends LuceneTestCase {

  public void testShortWriteAtFileSizeLimit() throws Exception {
    Path path = createTempDir();
    int sizeLimit = Math.toIntExact(Files.getFileStore(path).getBlockSize());
    FilterFileSystemProvider limited =
        new FilterFileSystemProvider("sizelimit://", path.getFileSystem()) {
          @Override
          public FileChannel newFileChannel(
              Path file, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
              throws IOException {
            return new FilterFileChannel(super.newFileChannel(file, options, attrs)) {
              @Override
              public int write(ByteBuffer src, long position) throws IOException {
                if (position >= sizeLimit) {
                  throw new IOException("File too large");
                }
                int n = (int) Math.min(src.remaining(), sizeLimit - position);
                int written = delegate.write(src.duplicate().limit(src.position() + n), position);
                src.position(src.position() + written);
                return written;
              }
            };
          }
        };
    try (Directory dir = new DirectIODirectory(FSDirectory.open(limited.wrapPath(path)))) {
      IndexOutput out = dir.createOutput("out", IOContext.merge());
      out.writeBytes(new byte[sizeLimit + 1], 0, sizeLimit + 1);
      // expected: close() throws, the file cannot hold the last byte; actual: close() returns
      expectThrows(IOException.class, out::close);
    }
  }
}
