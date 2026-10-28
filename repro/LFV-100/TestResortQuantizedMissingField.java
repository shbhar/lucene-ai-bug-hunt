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

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;

/** Lucene104 (OSQ) quantized formats; compiles against main and 10.4+/10.5.x. */
public class TestResortQuantizedMissingField extends ResortCase {

  @Override
  protected Codec codecWith(KnnVectorsFormat format) {
    return new Lucene104Codec() {
      @Override
      public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
        return format;
      }
    };
  }

  // --- the claim: these should pass, and fail if the bug is present ---

  public void testFlatScalarQuantizedResortSparseField() throws Exception {
    runCase(new Lucene104ScalarQuantizedVectorsFormat(), true, false);
  }

  public void testHnswScalarQuantizedResortSparseField() throws Exception {
    runCase(new Lucene104HnswScalarQuantizedVectorsFormat(), true, false);
  }

  // --- controls: expected to pass on main whether or not the bug is present ---

  /** Same index, non-quantized default HNSW format. */
  public void testControlHnswFloatResortSparseField() throws Exception {
    runCase(new Lucene99HnswVectorsFormat(), true, false);
  }

  /** Same quantized format and index, but plain addIndexes of the segment readers (no re-sort). */
  public void testControlHnswScalarQuantizedNoResort() throws Exception {
    runCase(new Lucene104HnswScalarQuantizedVectorsFormat(), false, false);
  }

  /** Same quantized format, re-sorted, but both segments have both fields. */
  public void testControlHnswScalarQuantizedResortDenseFields() throws Exception {
    runCase(new Lucene104HnswScalarQuantizedVectorsFormat(), true, true);
  }
}
