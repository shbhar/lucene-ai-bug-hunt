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

import java.io.IOException;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestForceMergeKeepsMergedSegmentAfterReopen extends LuceneTestCase {

  private static IndexWriterConfig config(Codec codec) {
    return new IndexWriterConfig()
        .setCodec(codec)
        .setMergePolicy(new LogByteSizeMergePolicy())
        .setMergeScheduler(new SerialMergeScheduler());
  }

  public void testForceMergeOfMergedIndexAfterReopen() throws IOException {
    Codec codec = new Lucene104Codec(Lucene104Codec.Mode.BEST_COMPRESSION);
    codec.compoundFormat().setShouldUseCompoundFile(false); // as MIGRATE.md shows
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(codec))) {
        w.addDocument(new Document());
        w.flush();
        w.addDocument(new Document());
        w.forceMerge(1);
      }
      String merged = SegmentInfos.readLatestCommit(dir).info(0).info.name;
      try (IndexWriter w = new IndexWriter(dir, config(codec))) {
        w.forceMerge(1);
      }
      // expected: the already merged segment is kept; actual: it is rewritten into a new segment
      assertEquals(
          "segment after forceMerge(1)",
          merged,
          SegmentInfos.readLatestCommit(dir).info(0).info.name);
    }
  }
}
