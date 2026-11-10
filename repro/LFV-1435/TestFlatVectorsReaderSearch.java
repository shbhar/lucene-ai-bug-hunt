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
package org.apache.lucene.codecs.hnsw;

import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99FlatVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFlatVectorsReaderSearch extends LuceneTestCase {

  public void testFlatFormatSearchScoresEveryVector() throws Exception {
    KnnVectorsFormat flat =
        new Lucene99FlatVectorsFormat(FlatVectorScorerUtil.getLucene99FlatVectorsScorer());
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(
                new FilterCodec("Flat", TestUtil.getDefaultCodec()) {
                  @Override
                  public KnnVectorsFormat knnVectorsFormat() {
                    return flat;
                  }
                });
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new KnnFloatVectorField("v", new float[] {1, 1}));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // javadoc: "it iterates every vector in the index and scores them"; expected 1 hit, actual 0
        assertEquals(1, new IndexSearcher(r).count(new KnnFloatVectorQuery("v", new float[] {1, 1}, 1)));
      }
    }
  }
}
