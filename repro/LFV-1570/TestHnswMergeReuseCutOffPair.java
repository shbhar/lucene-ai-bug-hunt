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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestHnswMergeReuseCutOffPair extends LuceneTestCase {

  private static final int ENOUGH_VECTORS_FOR_THE_DEFAULT_FORMAT_TO_BUILD_A_GRAPH = 700;

  public void testNearestDocumentsAreReturnedAfterOneDeleteAndForceMerge() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      add(w, "A", -10);
      add(w, "B", -11);
      add(w, "C", 0);
      for (int i = 0; i < ENOUGH_VECTORS_FOR_THE_DEFAULT_FORMAT_TO_BUILD_A_GRAPH; i++) {
        add(w, "r" + i, 10 + i);
      }
      w.commit();
      w.deleteDocuments(new Term("id", "C"));
      assertEquals("before the merge", List.of("A", "B"), twoNearestToA(w));
      w.forceMerge(1);
      // expected [A, B] (0 and 1 away from the query); actual [r0, r1] (20 and 21 away)
      assertEquals("after forceMerge(1)", List.of("A", "B"), twoNearestToA(w));
    }
  }

  private static void add(IndexWriter w, String id, float x) throws Exception {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new KnnFloatVectorField("v", new float[] {x}, VectorSimilarityFunction.EUCLIDEAN));
    w.addDocument(doc);
  }

  private static List<String> twoNearestToA(IndexWriter w) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(w)) {
      List<String> ids = new ArrayList<>();
      KnnFloatVectorQuery query = new KnnFloatVectorQuery("v", new float[] {-10}, 2);
      for (ScoreDoc hit : new IndexSearcher(reader).search(query, 2).scoreDocs) {
        ids.add(reader.storedFields().document(hit.doc).get("id"));
      }
      return ids;
    }
  }
}
