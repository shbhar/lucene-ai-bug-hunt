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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * KnnFloatVectorQuery accepts any k >= 1, but the per-leaf collector is created with a heap of
 * (about) k entries before the reader looks at how many vectors the leaf has: k = Integer.MAX_VALUE
 * throws IllegalArgumentException from TernaryLongHeap, and a k of 2^28 needs 2 GiB of heap even
 * for a three-document index.
 */
public class TestKnnQueryHugeK extends LuceneTestCase {

  private static TopDocs search(int k) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", new float[] {i, 1f}));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader); // single leaf, no executor
        return searcher.search(new KnnFloatVectorQuery("v", new float[] {1f, 1f}, k), 10);
      }
    }
  }

  /** Control: k larger than the index works and returns every vector. */
  public void testKLargerThanIndex() throws Exception {
    assertEquals(3, search(1000).scoreDocs.length);
  }

  /** k = Integer.MAX_VALUE ("all"): accepted by the constructor, fails at search time. */
  public void testKMaxValue() throws Exception {
    TopDocs td;
    try {
      td = search(Integer.MAX_VALUE);
    } catch (IllegalArgumentException e) {
      throw new AssertionError("search with k = Integer.MAX_VALUE threw", e);
    }
    assertEquals(3, td.scoreDocs.length);
  }

  /** A large k on a three-document index should not need memory proportional to k. */
  public void testLargeKSmallIndex() throws Exception {
    TopDocs td;
    try {
      td = search(1 << 28);
    } catch (OutOfMemoryError e) {
      throw new AssertionError(
          "search with k = 2^28 over 3 vectors ran out of memory (max heap "
              + (Runtime.getRuntime().maxMemory() >> 20)
              + " MiB)",
          e);
    }
    assertEquals(3, td.scoreDocs.length);
  }
}
