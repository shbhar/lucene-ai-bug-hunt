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
package org.apache.lucene.codecs;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.search.TopKnnCollector;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestKnnVectorsReaderSearchNullAcceptDocs extends LuceneTestCase {

  // javadoc: acceptDocs "or null if they are all allowed to match" -> expected 1 hit, actual NPE
  public void testNullAcceptDocs() throws Exception {
    KnnVectorsFormat in = TestUtil.getDefaultCodec().knnVectorsFormat();
    KnnVectorsReader[] reader = new KnnVectorsReader[1];
    KnnVectorsFormat format =
        new KnnVectorsFormat("Keep") {
          @Override
          public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
            return in.fieldsWriter(state);
          }

          @Override
          public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
            return reader[0] = in.fieldsReader(state);
          }

          @Override
          public int getMaxDimensions(String fieldName) {
            return in.getMaxDimensions(fieldName);
          }
        };
    Codec codec =
        new FilterCodec("Keep", TestUtil.getDefaultCodec()) {
          @Override
          public KnnVectorsFormat knnVectorsFormat() {
            return format;
          }
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
      Document doc = new Document();
      doc.add(new KnnFloatVectorField("v", new float[] {1f, 0f}));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        TopKnnCollector collector = new TopKnnCollector(1, Integer.MAX_VALUE);
        reader[0].search("v", new float[] {1f, 0f}, collector, null);
        assertEquals(1, collector.topDocs().scoreDocs.length);
      }
    }
  }
}
