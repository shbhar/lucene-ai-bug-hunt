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
package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDictionaryFailedLoadClosesTempOutput extends LuceneTestCase {

  public void testDictionaryReadErrorClosesTempOutput() throws Exception {
    MockDirectoryWrapper tempDir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    InputStream affix = new ByteArrayInputStream(new byte[0]);
    InputStream dictionary =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("simulated .dic read error");
          }
        };
    expectThrows(IOException.class, () -> new Dictionary(tempDir, "dict", affix, dictionary));
    // expected: tempDir closes; actual: "cannot close: there are still 1 open files"
    tempDir.close();
  }
}
