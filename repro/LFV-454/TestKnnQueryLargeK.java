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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * KnnFloatVectorQuery accepts any k >= 1 ("the number of documents to find"). Over a 3-document,
 * single-segment index, every k >= 3 should return the 3 documents.
 */
public class TestKnnQueryLargeK extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {i, 1f}));
        doc.add(new StringField("tag", "x", Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    assertEquals(1, reader.leaves().size());
    searcher = new IndexSearcher(reader); // plain searcher: one segment, no executor
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private int hits(int k, Query filter) throws Exception {
    Query q = new KnnFloatVectorQuery("v", new float[] {1f, 1f}, k, filter);
    return searcher.search(q, 10).scoreDocs.length;
  }

  /** Control: k = 1000, larger than the index, returns all 3 documents. */
  public void testK1000() throws Exception {
    assertEquals(3, hits(1000, null));
  }

  /** Contrast: the same k = Integer.MAX_VALUE works with a filter that matches every document. */
  public void testKMaxValueWithFilter() throws Exception {
    assertEquals(3, hits(Integer.MAX_VALUE, new TermQuery(new Term("tag", "x"))));
  }

  /** k = Integer.MAX_VALUE ("all"), no filter. */
  public void testKMaxValue() throws Exception {
    try {
      assertEquals(3, hits(Integer.MAX_VALUE, null));
    } catch (IllegalArgumentException e) {
      throw new AssertionError("k = Integer.MAX_VALUE threw", e);
    }
  }

  /** k = 2^28, no filter, over 3 documents. */
  public void testK2pow28() throws Exception {
    try {
      assertEquals(3, hits(1 << 28, null));
    } catch (OutOfMemoryError e) {
      throw new AssertionError(
          "k = 2^28 over 3 documents ran out of memory (max heap "
              + (Runtime.getRuntime().maxMemory() >> 20)
              + " MiB)",
          e);
    }
  }
}
