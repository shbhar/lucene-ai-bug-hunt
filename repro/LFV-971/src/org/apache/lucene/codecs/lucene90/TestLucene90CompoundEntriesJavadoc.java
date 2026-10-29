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

import java.util.Arrays;
import java.util.TreeSet;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.CompoundDirectory;
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

public class TestLucene90CompoundEntriesJavadoc extends LuceneTestCase {

  private static SegmentInfo writeCompoundSegment(Directory dir) throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(true);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(new Document());
    }
    return SegmentInfos.readLatestCommit(dir).info(0).info;
  }

  public void testEntryTableEndsAfterLastEntry() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      SegmentInfo si = writeCompoundSegment(dir);
      try (IndexInput in = dir.openInput(si.name + ".cfe", IOContext.READONCE)) {
        in.seek(CodecUtil.indexHeaderLength("Lucene90CompoundEntries", ""));
        for (int i = in.readVInt(); i > 0; i--) {
          in.readString();
          in.readLong();
          in.readLong();
        }
        // javadoc: .cfe ends after the last <FileName, DataOffset, DataLength>; written: a Footer
        assertEquals("bytes after the documented grammar", 0, in.length() - in.getFilePointer());
      }
    }
  }

  public void testFileNameIsTheFileName() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      SegmentInfo si = writeCompoundSegment(dir);
      TreeSet<String> fileNames = new TreeSet<>();
      try (IndexInput in = dir.openInput(si.name + ".cfe", IOContext.READONCE)) {
        in.seek(CodecUtil.indexHeaderLength("Lucene90CompoundEntries", ""));
        for (int i = in.readVInt(); i > 0; i--) {
          fileNames.add(in.readString());
          in.readLong();
          in.readLong();
        }
      }
      try (CompoundDirectory cfs = si.getCodec().compoundFormat().getCompoundReader(dir, si)) {
        // javadoc: FileName is "that file's name"; written: the name without the segment name
        assertEquals("FileName values", new TreeSet<>(Arrays.asList(cfs.listAll())), fileNames);
      }
    }
  }
}
