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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SoftDeletesDirectoryReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestKnnQueryOnSoftDeletesReader extends LuceneTestCase {

  // Expected: 1 hit (documents 500 to 999 are live). Actual: 0 hits.
  public void testKnnQueryWithoutFilterReturnsALiveDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setSoftDeletesField("soft")
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("vector", new float[] {i}));
          doc.add(new StringField("part", i < 500 ? "old" : "new", Field.Store.NO));
          writer.addDocument(doc);
        }
        writer.updateDocValues(new Term("part", "old"), new NumericDocValuesField("soft", 1));
      }
      try (DirectoryReader reader =
          new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), "soft")) {
        Query query = new KnnFloatVectorQuery("vector", new float[] {0}, 1);
        TopDocs hits = new IndexSearcher(reader).search(query, 1);
        assertEquals(
            "hits of a kNN query for 1 neighbour among 500 live documents",
            1,
            hits.scoreDocs.length);
      }
    }
  }
}
