/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

/**
 * Lucene90LiveDocsFormat#writeLiveDocs copies the live docs in batches of 1024 bits with an {@code
 * int offset} that is incremented by 1024 while {@code offset < bits.length()}. For a segment whose
 * maxDoc is greater than 2^31 - 1024 (and at most IndexWriter.MAX_DOCS = 2^31 - 129), the
 * increment after the last full batch wraps to Integer.MIN_VALUE and the loop continues with a
 * negative offset.
 */
public class TestLiveDocsWriteNearMaxDocs extends LuceneTestCase {

  private static void roundTrip(int maxDoc) throws Exception {
    FixedBitSet live = new FixedBitSet(maxDoc);
    live.set(0, maxDoc);
    live.clear(maxDoc - 1); // one deleted document, the last one
    live.clear(5);
    try (Directory dir = new ByteBuffersDirectory()) {
      Codec codec = Codec.getDefault();
      SegmentInfo si =
          new SegmentInfo(
              dir,
              Version.LATEST,
              Version.LATEST,
              "seg",
              maxDoc,
              false,
              false,
              codec,
              Collections.emptyMap(),
              StringHelper.randomId(),
              Collections.emptyMap(),
              null);
      SegmentCommitInfo sci = new SegmentCommitInfo(si, 0, 0, 0, -1, -1, StringHelper.randomId());
      Lucene90LiveDocsFormat format = new Lucene90LiveDocsFormat();
      format.writeLiveDocs(live, dir, sci, 2, IOContext.DEFAULT);

      sci = new SegmentCommitInfo(si, 2, 0, 1, -1, -1, StringHelper.randomId());
      Bits read = format.readLiveDocs(dir, sci, IOContext.READONCE);
      assertEquals(maxDoc, read.length());
      assertFalse(read.get(5));
      assertFalse(read.get(maxDoc - 1));
      assertTrue(read.get(0));
      assertTrue(read.get(maxDoc - 2));
    }
  }

  /** Control: the largest maxDoc for which the batch offset cannot wrap. Passes. */
  public void testMaxDocTwoPow31Minus1024() throws Exception {
    roundTrip(Integer.MAX_VALUE - 1023); // 2^31 - 1024
  }

  /** maxDoc = IndexWriter.MAX_DOCS, a legal segment size. Fails on main. */
  public void testMaxDocEqualsMaxDocs() throws Exception {
    roundTrip(IndexWriter.MAX_DOCS);
  }

  /** The smallest maxDoc that triggers the wrap. Fails on main. */
  public void testMaxDocTwoPow31Minus1023() throws Exception {
    roundTrip(Integer.MAX_VALUE - 1022); // 2^31 - 1023
  }
}
