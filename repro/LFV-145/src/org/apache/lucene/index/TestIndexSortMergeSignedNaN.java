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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestIndexSortMergeSignedNaN extends LuceneTestCase {

  public void testMergedSegmentFollowsIndexSort() throws IOException {
    float signedNaN = Float.intBitsToFloat(0xffc00000);
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setIndexSort(new Sort(new SortField("f", SortField.Type.FLOAT, true)));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (float[] segment : new float[][] {{3f, 1f, signedNaN}, {2f, 0.5f}}) {
        for (float v : segment) {
          Document doc = new Document();
          doc.add(new FloatDocValuesField("f", v));
          w.addDocument(doc);
        }
        w.flush();
      }
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        NumericDocValues dv = getOnlyLeafReader(reader).getNumericDocValues("f");
        List<Float> order = new ArrayList<>();
        while (dv.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          order.add(Float.intBitsToFloat((int) dv.longValue()));
        }
        // expected descending Float.compare order [NaN, 3, 2, 1, 0.5]; actual [2, 0.5, NaN, 3, 1]
        assertEquals(List.of(Float.NaN, 3f, 2f, 1f, 0.5f), order);
      }
    }
  }
}
