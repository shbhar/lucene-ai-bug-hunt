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
import java.util.Arrays;
import java.util.concurrent.Executor;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.SortingCodecReader;
import org.apache.lucene.index.Sorter;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SortingCodecReader's javadoc: re-sort an index "by wrapping all readers of the index with this
 * reader and adding it to a fresh IndexWriter via IndexWriter#addIndexes(CodecReader...)". When
 * that writer's merge reorders documents (BPReorderingMergePolicy, or any OneMerge.reorder), the
 * concatenated view of the input readers numbers vector ordinals by iteration position and ignores
 * the ordinal that a SortingCodecReader's iterator reports, so vectors end up on the wrong
 * documents in the new index. Only public API is used.
 */
public class TestReorderedAddIndexesOfSortingReaders extends LuceneTestCase {

  private static float[] vectorFor(int id) {
    return new float[] {id, 100 - id};
  }

  private static Directory buildIndex(int firstId, int numDocs) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int id = firstId; id < firstId + numDocs; id++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(id), Field.Store.YES));
        doc.add(new StoredField("idnum", id));
        doc.add(new NumericDocValuesField("rank", id));
        doc.add(new KnnFloatVectorField("vector", vectorFor(id)));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private void addAndCheck(MergePolicy mergePolicy) throws IOException {
    Directory a = buildIndex(0, 4);
    Directory b = buildIndex(10, 4);
    // re-sort each input by descending rank, as in the SortingCodecReader javadoc
    Sort byRankDesc = new Sort(new SortField("rank", SortField.Type.LONG, true));
    try (DirectoryReader ra = DirectoryReader.open(a);
        DirectoryReader rb = DirectoryReader.open(b);
        Directory target = newDirectory()) {
      CodecReader sa = SortingCodecReader.wrap((CodecReader) ra.leaves().get(0).reader(), byRankDesc);
      CodecReader sb = SortingCodecReader.wrap((CodecReader) rb.leaves().get(0).reader(), byRankDesc);
      try (IndexWriter w =
          new IndexWriter(target, new IndexWriterConfig().setMergePolicy(mergePolicy))) {
        w.addIndexes(sa, sb);
      }
      try (DirectoryReader r = DirectoryReader.open(target)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        StoredFields storedFields = leaf.storedFields();
        FloatVectorValues vectors = leaf.getFloatVectorValues("vector");
        assertEquals(8, vectors.size());
        KnnVectorValues.DocIndexIterator it = vectors.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          int id = storedFields.document(doc).getField("idnum").numericValue().intValue();
          float[] actual = vectors.vectorValue(it.index());
          assertArrayEquals(
              "doc " + doc + " (id " + id + ") has vector " + Arrays.toString(actual),
              vectorFor(id),
              actual,
              0f);
        }
      }
    } finally {
      a.close();
      b.close();
    }
  }

  /** Control: without reordering, the same addIndexes call keeps every vector on its document. */
  public void testControlWithoutReordering() throws IOException {
    addAndCheck(new LogDocMergePolicy());
  }

  /** The stock reordering merge policy with default settings. */
  public void testBPReorderingMergePolicy() throws IOException {
    addAndCheck(new BPReorderingMergePolicy(new LogDocMergePolicy(), new BPIndexReorderer()));
  }

  /** Any reorder() that returns a non-null DocMap triggers it, even the identity. */
  public void testIdentityReorder() throws IOException {
    addAndCheck(
        new FilterMergePolicy(new LogDocMergePolicy()) {
          @Override
          public MergeSpecification findMerges(CodecReader... readers) {
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(readers) {
                  @Override
                  public Sorter.DocMap reorder(CodecReader reader, Directory dir, Executor ex) {
                    final int maxDoc = reader.maxDoc();
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
        });
  }
}
