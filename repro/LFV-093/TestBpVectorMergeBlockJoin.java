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
package org.apache.lucene.misc.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.search.join.DiversifyingChildrenFloatKnnVectorQuery;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.search.join.ToParentBlockJoinQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The same 4-block index as TestBpVectorMergeBlocks (vectors on the children), queried with the
 * join module after the reordered merge. Needs lucene-join on the test classpath.
 */
public class TestBpVectorMergeBlockJoin extends LuceneTestCase {

  public void testBlockJoinAfterReorderedMerge() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setParentField("_parent")
              .setMergePolicy(
                  new BPReorderingMergePolicy(
                      new LogDocMergePolicy(), new BpVectorReorderer("vec")));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int b = 0; b < 4; b++) {
          List<Document> block = new ArrayList<>();
          for (int c = 0; c < 2; c++) {
            Document child = new Document();
            child.add(new StringField("id", "b" + b + "c" + c, Field.Store.YES));
            child.add(new KnnFloatVectorField("vec", new float[] {b + 1, c + 1}));
            block.add(child);
          }
          Document parent = new Document();
          parent.add(new StringField("id", "b" + b + "p", Field.Store.YES));
          parent.add(new StringField("type", "parent", Field.Store.NO));
          block.add(parent);
          w.addDocuments(block);
          if (b == 1) {
            w.commit();
          }
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        StoredFields storedFields = searcher.storedFields();
        BitSetProducer parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        List<String> joins = new ArrayList<>();
        int wrong = 0;
        for (int b = 0; b < 4; b++) {
          for (int c = 0; c < 2; c++) {
            String child = "b" + b + "c" + c;
            TopDocs hits =
                searcher.search(
                    new ToParentBlockJoinQuery(
                        new TermQuery(new Term("id", child)), parents, ScoreMode.None),
                    10);
            String got =
                hits.scoreDocs.length == 0
                    ? "none"
                    : storedFields.document(hits.scoreDocs[0].doc).get("id");
            joins.add(child + "->" + got);
            if (got.equals("b" + b + "p") == false) {
              wrong++;
            }
          }
        }
        // nested kNN: the best child of each of 3 different parents
        TopDocs knn =
            searcher.search(
                new DiversifyingChildrenFloatKnnVectorQuery(
                    "vec", new float[] {2.5f, 1.5f}, null, 3, parents),
                3);
        List<String> knnHits = new ArrayList<>();
        for (ScoreDoc sd : knn.scoreDocs) {
          knnHits.add(storedFields.document(sd.doc).get("id"));
        }
        System.out.println(
            "JOIN wrong="
                + wrong
                + "/8 joins="
                + joins
                + " diversifiedKnn(k=3) hits="
                + knn.scoreDocs.length
                + " "
                + knnHits);
        assertEquals("ToParentBlockJoinQuery: " + joins, 0, wrong);
        assertEquals("DiversifyingChildrenFloatKnnVectorQuery k=3: " + knnHits, 3, knnHits.size());
      }
    }
  }
}
