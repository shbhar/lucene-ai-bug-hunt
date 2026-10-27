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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * CheckIndex tests the vectors of FLOAT32 fields but silently skips FLOAT16 fields. The two tests
 * are the same index (2 fields, 3 vectors, as in BaseKnnVectorsFormatTestCase
 * #testCheckIndexIncludesVectors) with each encoding; only the float16 one fails.
 */
public class TestCheckIndexFloat16Vectors extends LuceneTestCase {

  private static CheckIndex.Status.VectorValuesStatus check(boolean float16, ByteArrayOutputStream out)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        add(doc, "v1", float16);
        w.addDocument(doc);
        add(doc, "v2", float16);
        w.addDocument(doc);
      }
      CheckIndex.Status status =
          TestUtil.checkIndex(dir, CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS, true, true, out);
      assertEquals(1, status.segmentInfos.size());
      return status.segmentInfos.get(0).vectorValuesStatus;
    }
  }

  private static void add(Document doc, String field, boolean float16) {
    if (float16) {
      short[] v = {
        Float.floatToFloat16(0.5f), Float.floatToFloat16(0.5f),
        Float.floatToFloat16(0.5f), Float.floatToFloat16(0.5f)
      };
      doc.add(new KnnFloat16VectorField(field, v, VectorSimilarityFunction.EUCLIDEAN));
    } else {
      float[] v = {0.5f, 0.5f, 0.5f, 0.5f};
      doc.add(new KnnFloatVectorField(field, v, VectorSimilarityFunction.EUCLIDEAN));
    }
  }

  public void testFloat32VectorsAreChecked() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CheckIndex.Status.VectorValuesStatus s = check(false, out);
    assertEquals(3, s.totalVectorValues);
    assertEquals(2, s.totalKnnVectorFields);
  }

  public void testFloat16VectorsAreChecked() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CheckIndex.Status.VectorValuesStatus s = check(true, out);
    String vectorsLine = "";
    for (String line : out.toString(UTF_8).split("\n")) {
      if (line.contains("test: vectors")) vectorsLine = line.trim();
    }
    // 3 float16 vector values were indexed across 2 fields; CheckIndex reports and tests none
    assertEquals(vectorsLine, 3, s.totalVectorValues);
    assertEquals(vectorsLine, 2, s.totalKnnVectorFields);
  }
}
