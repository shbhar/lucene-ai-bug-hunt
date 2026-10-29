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
package org.apache.lucene.codecs.lucene90;

import java.util.Collections;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

public class TestLiveDocsWriteMaxDocs extends LuceneTestCase {

  public void testWriteLiveDocsOfMaxDocsSegment() throws Exception {
    int maxDoc = IndexWriter.MAX_DOCS;
    FixedBitSet liveDocs = new FixedBitSet(maxDoc);
    liveDocs.set(0, maxDoc - 1);
    try (Directory dir = FSDirectory.open(createTempDir())) {
      SegmentInfo si =
          new SegmentInfo(
              dir,
              Version.LATEST,
              Version.LATEST,
              "_0",
              maxDoc,
              false,
              false,
              Codec.getDefault(),
              Collections.emptyMap(),
              StringHelper.randomId(),
              Collections.emptyMap(),
              null);
      SegmentCommitInfo sci = new SegmentCommitInfo(si, 0, 0, -1, -1, -1, StringHelper.randomId());
      try {
        new Lucene90LiveDocsFormat().writeLiveDocs(liveDocs, dir, sci, 1, IOContext.DEFAULT);
      } catch (Throwable t) {
        // Expected: the .liv file is written; actual: the int batch offset wraps, writeBits throws.
        throw new AssertionError("writeLiveDocs with maxDoc=IndexWriter.MAX_DOCS threw " + t, t);
      }
    }
  }
}
