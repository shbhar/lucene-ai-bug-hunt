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
package org.apache.lucene.sandbox.codecs.faiss;

import java.io.IOException;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.junit.BeforeClass;

public class TestFaissMergeAfterDelete extends LuceneTestCase {

  @BeforeClass
  public static void maybeSuppress() {
    if (Boolean.getBoolean("tests.faiss.run")) {
      return;
    }
    boolean dependenciesPresent;
    try {
      FaissLibrary _ = FaissLibrary.INSTANCE;
      dependenciesPresent = true;
    } catch (LinkageError _) {
      dependenciesPresent = false;
    }
    assumeTrue("Dependencies present", dependenciesPresent);
  }

  public void testMergeAfterDelete() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config =
          new IndexWriterConfig()
              .setCodec(TestUtil.alwaysKnnVectorsFormat(new FaissKnnVectorsFormat()));
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        writer.addDocument(doc("a", 3));
        writer.addDocument(doc("b", 4));
        writer.addDocument(doc("c", 5));
        writer.commit();
        writer.deleteDocuments(new Term("id", "a"));
        writer.forceMerge(1);
      }

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new KnnFloatVectorQuery("v", new float[] {1, 0}, 2), 2);

        StoredFields storedFields = searcher.storedFields();
        StringBuilder hits = new StringBuilder();
        for (ScoreDoc hit : topDocs.scoreDocs) {
          hits.append(
              String.format(
                  Locale.ROOT, "%s=%.4f ", storedFields.document(hit.doc).get("id"), hit.score));
        }
        // expected documents b and c; actual "b=0.5000 b=0.1000 ": b twice, once as a zero vector
        assertEquals("b=0.1000 c=0.0588 ", hits.toString());
      }
    }
  }

  private static Document doc(String id, float x) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new KnnFloatVectorField("v", new float[] {x, 0}, VectorSimilarityFunction.EUCLIDEAN));
    return doc;
  }
}
