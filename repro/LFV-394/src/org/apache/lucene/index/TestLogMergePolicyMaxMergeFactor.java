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

import java.util.Map;
import java.util.Set;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.BaseMergePolicyTestCase.MockMergeContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

public class TestLogMergePolicyMaxMergeFactor extends LuceneTestCase {

  private static SegmentCommitInfo segment(Directory dir, String name, int maxDoc) {
    SegmentInfo si =
        new SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            name,
            maxDoc,
            false,
            false,
            Codec.getDefault(),
            Map.of(),
            StringHelper.randomId(),
            Map.of(),
            null);
    si.setFiles(Set.of());
    return new SegmentCommitInfo(si, 0, 0, -1, -1, -1, StringHelper.randomId());
  }

  public void testFindMergesWithMaxMergeFactor() throws Exception {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(Integer.MAX_VALUE);
    try (Directory dir = new ByteBuffersDirectory()) {
      SegmentInfos infos = new SegmentInfos(Version.LATEST.major);
      infos.add(segment(dir, "_0", 20_000_000));
      infos.add(segment(dir, "_1", 1));
      try {
        assertNull(mp.findMerges(MergeTrigger.FULL_FLUSH, infos, new MockMergeContext(s -> 0)));
      } catch (RuntimeException e) {
        // Expected: null, no merge (as with Integer.MAX_VALUE - 1); actual: findMerges throws.
        throw new AssertionError("expected findMerges to return null, but it threw " + e, e);
      }
    }
  }
}
