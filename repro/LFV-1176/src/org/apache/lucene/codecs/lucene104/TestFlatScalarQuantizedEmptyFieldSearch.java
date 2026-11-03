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
package org.apache.lucene.codecs.lucene104;

import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.FloatVectorSimilarityQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFlatScalarQuantizedEmptyFieldSearch extends LuceneTestCase {

  public void testSimilarityQueryOnFieldWithNoVectors() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(
                new Lucene104Codec() {
                  @Override
                  public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
                    return new Lucene104ScalarQuantizedVectorsFormat();
                  }
                });
    float[] vector = {1f, 2f, 3f, 4f};
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", vector));
        w.addDocument(doc);
        w.addDocument(new Document());
        w.deleteDocuments(new FieldExistsQuery("v"));
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(0, r.leaves().get(0).reader().getFloatVectorValues("v").size());
        IndexSearcher searcher = new IndexSearcher(r);
        // expected: 0 hits; actual: NullPointerException because "quantizer" is null
        assertEquals(0, searcher.count(new FloatVectorSimilarityQuery.Adaptive("v", vector, 0f)));
      }
    }
  }
}
