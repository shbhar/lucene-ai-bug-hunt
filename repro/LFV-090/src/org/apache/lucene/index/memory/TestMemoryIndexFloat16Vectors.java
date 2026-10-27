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
package org.apache.lucene.index.memory;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.index.Float16VectorValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MemoryIndex has a FLOAT16 branch for vector fields, but adding a KnnFloat16VectorField fails a
 * leftover assertion that only admits float32 and byte vector fields.
 */
public class TestMemoryIndexFloat16Vectors extends LuceneTestCase {

  private static final short[] VECTOR = {
    Float.floatToFloat16(1f), Float.floatToFloat16(2f), Float.floatToFloat16(3f)
  };

  private static void assertFloat16Readable(MemoryIndex mi) throws Exception {
    LeafReader reader = (LeafReader) mi.createSearcher().getIndexReader();
    Float16VectorValues values = reader.getFloat16VectorValues("v");
    assertNotNull(values);
    assertEquals(1, values.size());
    assertArrayEquals(VECTOR, values.vectorValue(0));
  }

  public void testAddField() throws Exception {
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new KnnFloat16VectorField("v", VECTOR, VectorSimilarityFunction.EUCLIDEAN), null);
    assertFloat16Readable(mi);
  }

  public void testFromDocument() throws Exception {
    Document doc = new Document();
    doc.add(new KnnFloat16VectorField("v", VECTOR, VectorSimilarityFunction.EUCLIDEAN));
    MemoryIndex mi = MemoryIndex.fromDocument(doc, new StandardAnalyzer());
    assertFloat16Readable(mi);
  }
}
