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

public class TestFailedOpenReleasesWriteLock extends LuceneTestCase {

  public void testFailedOpenReleasesWriteLock() throws Exception {
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (message.startsWith("init: hit exception on init")) {
              throw new IllegalStateException("log sink failed");
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
      IndexWriterConfig conf =
          new IndexWriterConfig()
              .setOpenMode(IndexWriterConfig.OpenMode.APPEND)
              .setInfoStream(infoStream);
      expectThrows(Exception.class, () -> new IndexWriter(dir, conf));
      // expected: the failed open (APPEND, no index) released the write lock; actual: still held
      dir.obtainLock(IndexWriter.WRITE_LOCK_NAME).close();
    }
  }
}
