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

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSegmentsNJavadocVersionField extends LuceneTestCase {

  public void testFieldAfterLuceneVersionIsVersion() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      new IndexWriter(dir, new IndexWriterConfig()).close();
      SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
      try (IndexInput in = dir.openInput(sis.getSegmentsFileName(), IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(
            in,
            "segments",
            0,
            Integer.MAX_VALUE,
            sis.getId(),
            Long.toString(sis.getGeneration(), Character.MAX_RADIX));
        in.readVInt();
        in.readVInt();
        in.readVInt();
        // javadoc: LuceneVersion (3 vInts) then Version (Int64); written: IndexCreatedVersionMajor
        assertEquals("Version", sis.getVersion(), in.readLong());
      }
    }
  }
}
