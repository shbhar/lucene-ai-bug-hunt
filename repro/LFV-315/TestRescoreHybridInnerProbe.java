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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/** Probe: a hybrid (kNN OR term) inner query with SCORES and no query cache at all. */
public class TestRescoreHybridInnerProbe extends LuceneTestCase {
  public void testHybridNoCache() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new TextField("body", i < 9 ? "common" : "rare", Field.Store.NO));
          doc.add(
              new KnnFloatVectorField("v", new float[] {1, i}, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query knn = new KnnFloatVectorQuery("v", new float[] {1, 9}, 4);
        Query hybrid =
            new BooleanQuery.Builder()
                .add(knn, BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("body", "rare")), BooleanClause.Occur.SHOULD)
                .build();
        for (Query inner : new Query[] {knn, hybrid}) {
          String name = inner == knn ? "knn" : "hybrid";
          System.out.println(
              "OBS " + name + " plain=" + Arrays.toString(searcher.search(inner, 3).scoreDocs));
          System.out.println(
              "OBS "
                  + name
                  + " rescored="
                  + Arrays.toString(
                      searcher.search(new RescoreTopNQuery(inner, DoubleValuesSource.SCORES, 3), 3)
                          .scoreDocs));
        }
      }
    }
  }
}
