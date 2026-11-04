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
package org.apache.lucene.util;

import java.io.EOFException;
import java.io.IOException;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestOfflineSorterReopenFailure extends LuceneTestCase {

  public void testReadFailureKeptWhenChecksumReopenFails() throws Exception {
    Directory base = new ByteBuffersDirectory();
    try (IndexOutput out = base.createOutput("in", IOContext.DEFAULT)) {
      out.writeShort((short) 100); // entry length 100, but its bytes are missing
      CodecUtil.writeFooter(out);
    }
    Directory dir =
        new FilterDirectory(base) {
          int opens;

          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            if (++opens == 2) {
              throw new IOException("re-open failed");
            }
            return super.openInput(name, context);
          }
        };
    // expected: the EOFException of the truncated entry; actual: only IOException("re-open failed")
    expectThrows(EOFException.class, () -> new OfflineSorter(dir, "tmp").sort("in"));
  }
}
