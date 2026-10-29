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

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

import java.util.concurrent.Executor;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReorderedAddIndexesOfSortedReaders extends LuceneTestCase {

  private static MergePolicy identityReorder() {
    return new FilterMergePolicy(new LogDocMergePolicy()) {
      @Override
      public MergeSpecification findMerges(CodecReader... readers) {
        MergeSpecification spec = new MergeSpecification();
        spec.add(
            new OneMerge(readers) {
              @Override
              public Sorter.DocMap reorder(CodecReader reader, Directory dir, Executor executor) {
                int maxDoc = reader.maxDoc();
                return new Sorter.DocMap() {
                  @Override
                  public int oldToNew(int docID) {
                    return docID;
                  }

                  @Override
                  public int newToOld(int docID) {
                    return docID;
                  }

                  @Override
                  public int size() {
                    return maxDoc;
                  }
                };
              }
            });
        return spec;
      }
    };
  }

  public void testIdentityReorderKeepsVectors() throws Exception {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(src, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (int id = 0; id < 4; id++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("id", id));
          doc.add(new KnnFloatVectorField("v", new float[] {id}));
          w.addDocument(doc);
          if (id == 1) {
            w.flush();
          }
        }
      }
      Sort byIdDesc = new Sort(new SortField("id", SortField.Type.LONG, true));
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w =
              new IndexWriter(dst, new IndexWriterConfig().setMergePolicy(identityReorder()))) {
        w.addIndexes(
            SortingCodecReader.wrap((CodecReader) r.leaves().get(0).reader(), byIdDesc),
            SortingCodecReader.wrap((CodecReader) r.leaves().get(1).reader(), byIdDesc));
      }
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        LeafReader leaf = getOnlyLeafReader(r);
        NumericDocValues ids = leaf.getNumericDocValues("id");
        FloatVectorValues vectors = leaf.getFloatVectorValues("v");
        KnnVectorValues.DocIndexIterator it = vectors.iterator();
        for (int doc = it.nextDoc(); doc != NO_MORE_DOCS; doc = it.nextDoc()) {
          assertTrue(ids.advanceExact(doc));
          float[] vector = vectors.vectorValue(it.index());
          // expected: the doc with id N has vector {N}; actual: it has another document's vector
          assertEquals("vector of id " + ids.longValue(), ids.longValue(), vector[0], 0f);
        }
      }
    }
  }
}
