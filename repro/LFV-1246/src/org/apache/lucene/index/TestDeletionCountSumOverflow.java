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
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

public class TestDeletionCountSumOverflow extends LuceneTestCase {

  // Expected: CorruptIndexException (delCount + softDelCount = 2^31 > maxDoc = 2^30); actual: the int sum wraps and the commit is read.
  public void testDeletionCountSumAboveMaxDoc() throws Exception {
    int maxDoc = 1 << 30;
    Codec codec = TestUtil.getDefaultCodec();
    try (Directory dir = new ByteBuffersDirectory()) {
      SegmentInfo si =
          new SegmentInfo(
              dir, Version.LATEST, Version.LATEST, "_0", maxDoc, false, false, codec, Map.of(),
              StringHelper.randomId(), Map.of(), null);
      si.setFiles(Set.of());
      codec.segmentInfoFormat().write(dir, si, IOContext.DEFAULT);
      SegmentInfos infos = new SegmentInfos(Version.LATEST.major);
      infos.add(new SegmentCommitInfo(si, maxDoc, maxDoc, -1, -1, -1, StringHelper.randomId()));
      infos.commit(dir);
      expectThrows(CorruptIndexException.class, () -> SegmentInfos.readLatestCommit(dir));
    }
  }
}
