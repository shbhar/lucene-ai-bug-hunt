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
package org.apache.lucene.codecs.bloom;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.index.DocValuesSkipIndexType;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

/**
 * DefaultBloomFilterFactory sizes the filter of a field from the segment's maxDoc "assuming all
 * the docs have a unique term" with a target false-positive rate of about 10%. For a segment of
 * 226,279,579 docs or more (well below IndexWriter.MAX_DOCS), FuzzySet.createOptimalSet computes
 * {@code 2 * setSize} in int, which overflows; the filter then has 64 bits and zero hash
 * functions, so it answers MAYBE for every term and never filters an absent term.
 */
public class TestBloomFilterLargeSegment extends LuceneTestCase {

  private static FuzzySet defaultFilterForSegment(Directory dir, int maxDoc) {
    SegmentInfo si =
        new SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            "_0",
            maxDoc,
            false,
            false,
            Codec.getDefault(),
            Map.of(),
            StringHelper.randomId(),
            Map.of(),
            null);
    FieldInfo id =
        new FieldInfo(
            "id",
            0,
            false,
            true,
            false,
            IndexOptions.DOCS,
            DocValuesType.NONE,
            DocValuesSkipIndexType.NONE,
            -1,
            new HashMap<>(),
            0,
            0,
            0,
            0,
            VectorEncoding.FLOAT32,
            VectorSimilarityFunction.EUCLIDEAN,
            false,
            false);
    SegmentWriteState state =
        new SegmentWriteState(
            InfoStream.NO_OUTPUT, dir, si, new FieldInfos(new FieldInfo[] {id}), null, IOContext.DEFAULT);
    return new DefaultBloomFilterFactory().getSetForField(state, id);
  }

  /** Adds 1,000 keys and counts how many of 1,000 absent keys the filter rejects. */
  private static int rejectedAbsentKeys(FuzzySet set) {
    for (int i = 0; i < 1000; i++) {
      set.addValue(new BytesRef("id" + i));
    }
    int no = 0;
    for (int i = 0; i < 1000; i++) {
      if (set.contains(new BytesRef("absent" + i)) == FuzzySet.ContainsResult.NO) {
        no++;
      }
    }
    return no;
  }

  /** A segment of 226,279,579 docs (about 10.5% of IndexWriter.MAX_DOCS). */
  public void testDefaultFilterStillFiltersForLargeSegment() throws Exception {
    try (Directory dir = newDirectory()) {
      FuzzySet set = defaultFilterForSegment(dir, 226_279_579);
      int no = rejectedAbsentKeys(set);
      assertTrue(
          "a ~10% false-positive filter must reject most absent keys, but rejected "
              + no
              + "/1000; filter="
              + set,
          no > 500);
    }
  }

  /** Control: one document less gives a 2^30-bit filter with 3 hash functions, which works. */
  public void testControlOneDocLess() throws Exception {
    try (Directory dir = newDirectory()) {
      FuzzySet set = defaultFilterForSegment(dir, 226_279_578);
      int no = rejectedAbsentKeys(set);
      assertTrue("rejected " + no + "/1000; filter=" + set, no > 500);
    }
  }
}
