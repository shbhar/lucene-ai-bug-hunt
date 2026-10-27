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
package org.apache.lucene.search.join;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * DiversifyingChildrenFloatKnnVectorQuery documents "If null, the default strategy will be used"
 * for its searchStrategy argument; with null it must return the same hits as the default.
 */
public class TestDiversifyingKnnNullSearchStrategy extends LuceneTestCase {

  public void testNullStrategyMultiSegmentBlockJoin() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        int x = 0;
        for (int s = 0; s < 10; s++) { // 10 segments of 400 blocks: 5 children + 1 parent
          for (int b = 0; b < 400; b++) {
            List<Document> block = new ArrayList<>();
            for (int c = 0; c < 5; c++) {
              Document child = new Document();
              child.add(
                  new KnnFloatVectorField("v", new float[] {x++, 1}, VectorSimilarityFunction.EUCLIDEAN));
              block.add(child);
            }
            Document parent = new Document();
            parent.add(new StringField("type", "parent", Field.Store.NO));
            block.add(parent);
            w.addDocuments(block);
          }
          w.commit();
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        BitSetProducer parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        float[] target = {0, 1};
        TopDocs expected =
            searcher.search(
                new DiversifyingChildrenFloatKnnVectorQuery("v", target, null, 50, parents), 50);
        TopDocs actual =
            searcher.search(
                new DiversifyingChildrenFloatKnnVectorQuery("v", target, null, 50, parents, null), 50);
        assertArrayEquals(
            Arrays.stream(expected.scoreDocs).mapToInt(sd -> sd.doc).toArray(),
            Arrays.stream(actual.scoreDocs).mapToInt(sd -> sd.doc).toArray());
      }
    }
  }
}
