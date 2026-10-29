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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCheckIndexFloat16Vectors extends LuceneTestCase {

  public void testFloat16VectorFieldIsChecked() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        short[] v = {Float.floatToFloat16(1f), Float.floatToFloat16(2f)};
        doc.add(new KnnFloat16VectorField("v", v, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        CheckIndex.Status.VectorValuesStatus s =
            checker.checkIndex().segmentInfos.get(0).vectorValuesStatus;
        // 1 FLOAT16 field with 1 vector was indexed; CheckIndex reports 0 fields, 0 vectors
        assertEquals("vector fields checked", 1, s.totalKnnVectorFields);
        assertEquals("vector values checked", 1, s.totalVectorValues);
      }
    }
  }
}
