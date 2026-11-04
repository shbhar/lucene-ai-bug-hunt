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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.TermVectorsFormat;
import org.apache.lucene.codecs.compressing.CompressionMode;
import org.apache.lucene.codecs.compressing.Compressor;
import org.apache.lucene.codecs.compressing.Decompressor;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDataInput;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestTermVectorsWriterClosesCompressor extends LuceneTestCase {

  public void testCloseReleasesCompressor() throws IOException {
    AtomicInteger unclosed = new AtomicInteger();
    CompressionMode mode =
        new CompressionMode() {
          @Override
          public Compressor newCompressor() {
            unclosed.incrementAndGet();
            Compressor in = CompressionMode.HIGH_COMPRESSION.newCompressor();
            return new Compressor() {
              @Override
              public void compress(ByteBuffersDataInput buffersInput, DataOutput out)
                  throws IOException {
                in.compress(buffersInput, out);
              }

              @Override
              public void close() throws IOException {
                unclosed.decrementAndGet();
                in.close();
              }
            };
          }

          @Override
          public Decompressor newDecompressor() {
            return CompressionMode.HIGH_COMPRESSION.newDecompressor();
          }
        };
    TermVectorsFormat tvFormat =
        new Lucene90CompressingTermVectorsFormat("TV", "", mode, 1 << 12, 128, 10);
    FilterCodec codec =
        new FilterCodec(TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
          @Override
          public TermVectorsFormat termVectorsFormat() {
            return tvFormat;
          }
        };
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setStoreTermVectors(true);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
      Document doc = new Document();
      doc.add(new Field("f", "a", ft));
      w.addDocument(doc);
    }
    // expected: the closed term-vectors writer closed its compressor (0 left); actual on main: 1 left open
    assertEquals(0, unclosed.get());
  }
}
