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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestKnnQueryKMaxValue extends LuceneTestCase {

  public void testKMaxValue() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", new float[] {i, 1}));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query query = new KnnFloatVectorQuery("v", new float[] {1, 1}, Integer.MAX_VALUE);
        try {
          assertEquals(3, new IndexSearcher(reader).search(query, 10).scoreDocs.length);
        } catch (IllegalArgumentException e) {
          // expected: all 3 vectors (k is "the number of documents to find"); actual: this exception
          throw new AssertionError("expected 3 hits for k = Integer.MAX_VALUE, but got " + e, e);
        }
      }
    }
  }
}
