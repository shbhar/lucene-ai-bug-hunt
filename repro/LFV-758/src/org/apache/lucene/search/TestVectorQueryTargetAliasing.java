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
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The kNN vector queries keep the caller's target array instead of copying it, so a query vector
 * buffer that the caller reuses for its next query silently changes the earlier query: what it
 * matches and what it is equal to. getTargetCopy() already returns a copy so that callers cannot
 * reach the query's internal array; the constructor hands the caller that same access.
 */
public class TestVectorQueryTargetAliasing extends LuceneTestCase {

  private static final float[][] FLOATS = {{1, 0}, {0, 1}, {-1, 0}};
  private static final byte[][] BYTES = {{10, 0}, {0, 10}, {-10, 0}};

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < FLOATS.length; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        doc.add(new KnnFloatVectorField("f", FLOATS[i], VectorSimilarityFunction.EUCLIDEAN));
        doc.add(new KnnByteVectorField("b", BYTES[i], VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private String topId(Query q) throws Exception {
    TopDocs td = searcher.search(q, 1);
    return searcher.storedFields().document(td.scoreDocs[0].doc).get("id");
  }

  /** One reused query-vector buffer, two queries: the first one is changed by the second fill. */
  public void testKnnFloatVectorQueryKeepsCallerTarget() throws Exception {
    float[] buffer = {1, 0}; // the query vector for "nearest to doc 0"
    Query first = new KnnFloatVectorQuery("f", buffer, 1);
    Query firstFromCopy = new KnnFloatVectorQuery("f", buffer.clone(), 1);
    assertEquals(firstFromCopy, first);

    // the caller reuses its buffer for the next query vector ("nearest to doc 1")
    buffer[0] = 0;
    buffer[1] = 1;
    Query second = new KnnFloatVectorQuery("f", buffer, 1);

    assertEquals("0", topId(firstFromCopy));
    assertEquals("1", topId(second));
    assertEquals("the first query was built for (1, 0)", "0", topId(first));
    assertEquals("the first query was built for (1, 0)", firstFromCopy, first);
    assertFalse("queries built for different vectors", first.equals(second));
  }

  public void testKnnByteVectorQueryKeepsCallerTarget() throws Exception {
    byte[] buffer = {10, 0};
    Query first = new KnnByteVectorQuery("b", buffer, 1);
    Query firstFromCopy = new KnnByteVectorQuery("b", buffer.clone(), 1);
    buffer[0] = 0;
    buffer[1] = 10;
    Query second = new KnnByteVectorQuery("b", buffer, 1);

    assertEquals("0", topId(firstFromCopy));
    assertEquals("1", topId(second));
    assertEquals("the first query was built for (10, 0)", "0", topId(first));
    assertFalse("queries built for different vectors", first.equals(second));
  }

  /** The vector similarity (threshold) queries share the same pattern (main API). */
  public void testFloatVectorSimilarityQueryKeepsCallerTarget() throws Exception {
    float[] buffer = {1, 0};
    // documents within euclidean similarity 0.9 of (1, 0): only doc 0
    Query first = new FloatVectorSimilarityQuery.Adaptive("f", buffer, 0.9f);
    Query firstFromCopy = new FloatVectorSimilarityQuery.Adaptive("f", buffer.clone(), 0.9f);
    buffer[0] = 0;
    buffer[1] = 1;
    Query second = new FloatVectorSimilarityQuery.Adaptive("f", buffer, 0.9f);

    assertEquals("0", topId(firstFromCopy));
    assertEquals("1", topId(second));
    assertEquals("the first query was built for (1, 0)", "0", topId(first));
    assertFalse("queries built for different vectors", first.equals(second));
  }
}
