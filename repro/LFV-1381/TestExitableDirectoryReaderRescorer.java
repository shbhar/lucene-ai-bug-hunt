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

import java.io.IOException;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

public class TestExitableDirectoryReaderRescorer extends LuceneTestCase {

  public void testRescorerIsFullPrecision() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(
                  TestUtil.alwaysKnnVectorsFormat(
                      new Lucene104ScalarQuantizedVectorsFormat(
                          ScalarEncoding.SINGLE_BIT_QUERY_NIBBLE)));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (float[] v : new float[][] {{4, -1, -4, 4}, {-4, 3, 1, -2}}) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader =
          ExitableDirectoryReader.wrap(DirectoryReader.open(dir), () -> false)) {
        FloatVectorValues values = getOnlyLeafReader(reader).getFloatVectorValues("v");
        VectorScorer rescorer = values.rescorer(new float[] {3, -2, -2, 4});
        assertEquals(0, rescorer.iterator().nextDoc());
        // expected exact 1/(1+6) = 1/7 from the raw vectors; actual is the 1-bit quantized score
        assertEquals(1f / 7, rescorer.score(), 0f);
      }
    }
  }
}
