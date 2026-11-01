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
package org.apache.lucene.misc.index;

import java.util.Arrays;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestMultiPassIndexSplitterRoundRobinOverflow extends LuceneTestCase {

  public void testPart0OfMaxDocsIndex() throws Exception {
    Codec.setDefault(TestUtil.getDefaultCodec());
    Directory blockDir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(blockDir, new IndexWriterConfig())) {
      for (int i = 0; i < IndexWriter.MAX_DOCS / 257; i++) {
        w.addDocument(new Document());
      }
      w.forceMerge(1);
    }
    Directory input = new ByteBuffersDirectory();
    try (DirectoryReader block = DirectoryReader.open(blockDir);
        IndexWriter w = new IndexWriter(input, new IndexWriterConfig())) {
      CodecReader[] copies = new CodecReader[257];
      Arrays.fill(copies, (CodecReader) block.leaves().get(0).reader());
      w.addIndexes(copies);
    }

    Directory part0 = new ByteBuffersDirectory();
    Directory stopAfterPart0 = new ByteBuffersDirectory();
    stopAfterPart0.close();
    Directory[] outputs = new Directory[145];
    Arrays.fill(outputs, stopAfterPart0);
    outputs[0] = part0;
    try (DirectoryReader in = DirectoryReader.open(input)) {
      expectThrows(
          AlreadyClosedException.class,
          () -> new MultiPassIndexSplitter().split(in, outputs, false));
    }
    try (DirectoryReader p = DirectoryReader.open(part0)) {
      // MAX_DOCS = 257 * 8,355,967 docs; part 0 must hold exactly the 14,810,232 ids j with j % 145 == 0
      assertEquals("numDocs of round-robin part 0 of 145", 14_810_232, p.numDocs());
    }
  }
}
