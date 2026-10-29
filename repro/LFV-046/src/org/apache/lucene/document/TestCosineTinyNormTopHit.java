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
package org.apache.lucene.document;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCosineTinyNormTopHit extends LuceneTestCase {

  public void testIdenticalVectorIsTopHit() throws Exception {
    VectorSimilarityFunction cosine = VectorSimilarityFunction.COSINE;
    Document tiny = new Document();
    try {
      tiny.add(new KnnFloatVectorField("v", new float[] {1e-30f, 0f}, cosine));
    } catch (IllegalArgumentException rejectedLikeTheZeroVector) {
      return;
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document exact = new Document();
        exact.add(new KnnFloatVectorField("v", new float[] {0.6f, 0.8f}, cosine));
        w.addDocument(exact);
        w.addDocument(tiny);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        ScoreDoc top =
            new IndexSearcher(r)
                .search(new KnnFloatVectorQuery("v", new float[] {0.6f, 0.8f}, 2), 2)
                .scoreDocs[0];
        // expected: doc 0 {0.6, 0.8} on top; actual: doc 1 {1e-30, 0} on top, score +Inf
        assertEquals("top hit (score " + top.score + ")", 0, top.doc);
      }
    }
  }
}
