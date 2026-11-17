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

import java.util.Collection;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Version;

public class TestSegmentInfosCommitErrorLeavesPendingFile extends LuceneTestCase {

  // Expected: the failed commit leaves no file (10.6.0). Actual: pending_segments_1 stays.
  public void testFailedCommitRemovesPendingSegmentsFile() throws Exception {
    try (Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public void sync(Collection<String> names) {
            throw new OutOfMemoryError("simulated");
          }
        }) {
      expectThrows(
          OutOfMemoryError.class, () -> new SegmentInfos(Version.LATEST.major).commit(dir));
      assertArrayEquals(new String[0], dir.listAll());
    }
  }
}
