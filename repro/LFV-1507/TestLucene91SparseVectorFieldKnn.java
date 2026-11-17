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
package org.apache.lucene.backward_codecs.lucene91;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLucene91SparseVectorFieldKnn extends LuceneTestCase {

  public void testKnnHitIsTheDocumentThatHasTheVector() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig config = new IndexWriterConfig().setCodec(new Lucene91RWCodec());
    try (IndexWriter writer = new IndexWriter(dir, config)) {
      writer.addDocument(new Document());
      Document doc = new Document();
      doc.add(new KnnFloatVectorField("v", new float[] {1, 0}));
      writer.addDocument(doc);
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      KnnFloatVectorQuery query = new KnnFloatVectorQuery("v", new float[] {1, 0}, 1);
      // expected: document 1, the only one with a vector; on 9.8.0 to 9.12.3 the hit is document 0
      assertEquals(1, new IndexSearcher(reader).search(query, 1).scoreDocs[0].doc);
    }
  }
}
