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
package org.apache.lucene.codecs.lucene99;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLucene99SegmentInfoFormatJavadoc extends LuceneTestCase {

  public void testSegVersionIsTheDocumentedString() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(new Document());
      }
      SegmentInfo si = SegmentInfos.readLatestCommit(dir).info(0).info;
      try (IndexInput in = dir.openInput(si.name + ".si", IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(in, "Lucene90SegmentInfo", 0, 0, si.getId(), "");
        // javadoc: .si = Header, SegVersion (String), ...; the writer stores SegVersion as 3 Int32s
        String segVersion = in.readString();
        assertEquals(
            "SegVersion chars " + segVersion.chars().boxed().toList(),
            si.getVersion().toString(),
            segVersion);
      }
    }
  }
}
