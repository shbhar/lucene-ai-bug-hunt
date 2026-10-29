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
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestByteBuffersDirectoryRenameDropsNewSource extends LuceneTestCase {

  public void testFileCreatedUnderSourceNameDuringRenameSurvives() throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    Field files = ByteBuffersDirectory.class.getDeclaredField("files");
    files.setAccessible(true);
    files.set(
        dir,
        new ConcurrentHashMap<String, Object>() {
          @Override
          public boolean remove(Object key, Object value) {
            boolean removed = super.remove(key, value);
            try {
              dir.createOutput("a", IOContext.DEFAULT).close();
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
            return removed;
          }
        });
    dir.createOutput("a", IOContext.DEFAULT).close();
    dir.rename("a", "b");
    // expected: the new "a" created right after rename's checked remove survives; actual: deleted
    assertEquals(List.of("a", "b"), List.of(dir.listAll()));
    dir.close();
  }
}
