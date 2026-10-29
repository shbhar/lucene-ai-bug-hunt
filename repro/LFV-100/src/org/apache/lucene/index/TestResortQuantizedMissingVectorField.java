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
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestResortQuantizedMissingVectorField extends LuceneTestCase {

  private static Document doc(long sortValue, String vectorField) {
    Document doc = new Document();
    doc.add(new NumericDocValuesField("sort", sortValue));
    doc.add(new KnnFloatVectorField(vectorField, new float[] {1, 2}));
    return doc;
  }

  public void testResortSegmentLackingQuantizedField() throws Exception {
    Codec codec = TestUtil.alwaysKnnVectorsFormat(new Lucene104ScalarQuantizedVectorsFormat());
    Sort sort = new Sort(new SortField("sort", SortField.Type.LONG));
    try (Directory src = new ByteBuffersDirectory();
        Directory dst = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              src,
              new IndexWriterConfig().setCodec(codec).setMergePolicy(NoMergePolicy.INSTANCE))) {
        w.addDocument(doc(0, "a"));
        w.commit();
        w.addDocument(doc(2, "b"));
        w.addDocument(doc(1, "b"));
      }
      try (DirectoryReader reader = DirectoryReader.open(src);
          IndexWriter w =
              new IndexWriter(
                  dst,
                  new IndexWriterConfig()
                      .setCodec(codec)
                      .setIndexSort(sort)
                      .setMergeScheduler(new SerialMergeScheduler()))) {
        CodecReader[] sorted = new CodecReader[reader.leaves().size()];
        for (int i = 0; i < sorted.length; i++) {
          sorted[i] = SortingCodecReader.wrap((CodecReader) reader.leaves().get(i).reader(), sort);
        }
        // expected: all 3 docs are added; actual: NullPointerException (-da) or AssertionError (-ea)
        w.addIndexes(sorted);
        assertEquals(3, w.getDocStats().maxDoc);
      }
    }
  }
}
