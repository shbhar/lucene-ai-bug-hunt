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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDeleteByKnnQuery extends LuceneTestCase {

  public void testDeleteMatchesSearchOnTwoSegments() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (float x : new float[] {0, 10}) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {x, 0}));
        w.addDocument(doc);
        w.commit();
      }
      Query query = new KnnFloatVectorQuery("v", new float[] {0, 0}, 1);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, new IndexSearcher(reader).count(query));
      }
      w.deleteDocuments(query);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // expected: only the 1 search hit is deleted (1 doc left); actual: the top 1 of each segment
        assertEquals("live docs after deleteDocuments(k=1)", 1, reader.numDocs());
      }
    }
  }
}
