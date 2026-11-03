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
package org.apache.lucene.codecs.lucene90.compressing;

import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.TermVectorsFormat;
import org.apache.lucene.codecs.compressing.CompressionMode;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestTermVectorsBlockSize extends LuceneTestCase {

  public void testAcceptedBlockSizeCanWriteTermVectors() throws Exception {
    // Expected: blockSize 1024 (a chunk count, per the javadoc) is rejected or works; actual: IAE at flush.
    TermVectorsFormat format;
    try {
      format =
          new Lucene90CompressingTermVectorsFormat("TV", "", CompressionMode.FAST, 4096, 128, 1024);
    } catch (IllegalArgumentException e) {
      return;
    }
    FilterCodec codec =
        new FilterCodec("TestCodec", TestUtil.getDefaultCodec()) {
          @Override
          public TermVectorsFormat termVectorsFormat() {
            return format;
          }
        };
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setStoreTermVectors(true);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
      Document doc = new Document();
      doc.add(new Field("f", "a b", ft));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(2, r.termVectors().get(0, "f").size());
      }
    }
  }
}
