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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLucene99VectorsFormatsMetaJavadoc extends LuceneTestCase {

  private static int similarityOrdinal(Directory dir, String ext, String codec, byte[] id)
      throws IOException {
    String suffix = "Lucene99HnswVectorsFormat_0";
    try (IndexInput in = dir.openInput("_0_" + suffix + "." + ext, IOContext.READONCE)) {
      CodecUtil.checkIndexHeader(in, codec, 0, Integer.MAX_VALUE, id, suffix);
      assertEquals("field number", 0, in.readInt());
      return in.readInt();
    }
  }

  public void testDocumentedFieldSequence() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {1, 0}, VectorSimilarityFunction.COSINE));
        w.addDocument(doc);
      }
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      int cosine = VectorSimilarityFunction.COSINE.ordinal();
      // expected COSINE (2) in .vemf and .vem; actual 1 in both: the undocumented FLOAT32 encoding
      assertEquals(
          "[int32] vector similarity function ordinal in .vemf, .vem",
          List.of(cosine, cosine),
          List.of(
              similarityOrdinal(dir, "vemf", "Lucene99FlatVectorsFormatMeta", id),
              similarityOrdinal(dir, "vem", "Lucene99HnswVectorsFormatMeta", id)));
    }
  }
}
