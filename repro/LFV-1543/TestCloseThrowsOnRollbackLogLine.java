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
package org.apache.lucene.index;

import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestCloseThrowsOnRollbackLogLine extends LuceneTestCase {

  public void testCloseThrowsOnRollbackLogLine() throws Exception {
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (message.equals("rollback")) {
              throw new RuntimeException("BOOM!");
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return true;
          }

          @Override
          public void close() {}
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setInfoStream(infoStream));
      expectThrows(RuntimeException.class, w::close);
      // expected: a close() that throws leaves the writer closed, the write lock free; actual: held
      dir.obtainLock(IndexWriter.WRITE_LOCK_NAME).close();
    }
  }
}
