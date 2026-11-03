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
import org.apache.lucene.util.SuppressForbidden;

public class TestByteBuffersDirectoryFailedRenameLeavesDest extends LuceneTestCase {

  @SuppressForbidden(reason = "swaps the private files map to force the interleaving")
  public void testFailedRenameLeavesNoDest() throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    Field files = ByteBuffersDirectory.class.getDeclaredField("files");
    files.setAccessible(true);
    files.set(
        dir,
        new ConcurrentHashMap<String, Object>() {
          @Override
          public Object putIfAbsent(String key, Object value) {
            Object previous = super.putIfAbsent(key, value);
            if (key.equals("y")) {
              try {
                dir.rename("a", "x");
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            }
            return previous;
          }
        });
    dir.createOutput("a", IOContext.DEFAULT).close();
    expectThrows(IllegalStateException.class, () -> dir.rename("a", "y"));
    // expected: the rename to "y" threw, so only "x" is left; actual: [x, y]
    assertEquals(List.of("x"), List.of(dir.listAll()));
    dir.close();
  }
}
