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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * addIndexes(CodecReader...) must keep every document's vectors when the inputs were re-sorted with
 * SortingCodecReader and the merge policy reorders the addIndexes merge (OneMerge#reorder, as
 * BPReorderingMergePolicy does). The reorder here is the identity, so it moves nothing.
 */
public class TestReorderedAddIndexesVectors extends LuceneTestCase {

  /** A document's vectors name their owner: {id, 100 - id}. */
  private static float[] floatVector(int id) {
    return new float[] {id, 100 - id};
  }

  private static byte[] byteVector(int id) {
    return new byte[] {(byte) id, (byte) (100 - id)};
  }

  /** One segment with ids firstId .. firstId + 3, rank = id; optionally delete the first id. */
  private static Directory buildInput(int firstId, boolean deleteFirstId) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int id = firstId; id < firstId + 4; id++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
        doc.add(new StoredField("idnum", id));
        doc.add(new NumericDocValuesField("rank", id));
        doc.add(new KnnFloatVectorField("float", floatVector(id)));
        doc.add(new KnnByteVectorField("byte", byteVector(id)));
        w.addDocument(doc);
      }
      w.commit();
      if (deleteFirstId) {
        w.deleteDocuments(new Term("id", Integer.toString(firstId)));
      }
    }
    return dir;
  }

  /** An addIndexes merge whose OneMerge#reorder returns the identity DocMap. */
  private static MergePolicy identityReorderOnAddIndexes() {
    return new FilterMergePolicy(new LogDocMergePolicy()) {
      @Override
      public MergeSpecification findMerges(CodecReader... readers) {
        MergeSpecification spec = new MergeSpecification();
        spec.add(
            new OneMerge(readers) {
              @Override
              public Sorter.DocMap reorder(CodecReader reader, Directory dir, Executor executor) {
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
    };
  }

  /**
   * Inputs: ids 0-3 and ids 10-13, each re-sorted by descending rank with SortingCodecReader, added
   * in one addIndexes call to a writer without an index sort. Returns the documents whose float or
   * byte vector belongs to another document.
   */
  private static List<String> addIndexesAndFindWrongVectors(
      MergePolicy mergePolicy, boolean deleteId0) throws IOException {
    Sort byRankDesc = new Sort(new SortField("rank", SortField.Type.LONG, true));
    List<String> wrong = new ArrayList<>();
    try (Directory a = buildInput(0, deleteId0);
        Directory b = buildInput(10, false);
        DirectoryReader ra = DirectoryReader.open(a);
        DirectoryReader rb = DirectoryReader.open(b);
        Directory target = newDirectory()) {
      CodecReader sa =
          SortingCodecReader.wrap((CodecReader) ra.leaves().get(0).reader(), byRankDesc);
      CodecReader sb =
          SortingCodecReader.wrap((CodecReader) rb.leaves().get(0).reader(), byRankDesc);
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setMergePolicy(mergePolicy);
      try (IndexWriter w = new IndexWriter(target, iwc)) {
        w.addIndexes(sa, sb);
      }
      try (DirectoryReader r = DirectoryReader.open(target)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        assertEquals(deleteId0 ? 7 : 8, leaf.numDocs());
        StoredFields storedFields = leaf.storedFields();
        FloatVectorValues floats = leaf.getFloatVectorValues("float");
        KnnVectorValues.DocIndexIterator it = floats.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          int id = storedFields.document(doc).getField("idnum").numericValue().intValue();
          int owner = (int) floats.vectorValue(it.index())[0];
          if (owner != id) {
            wrong.add("doc " + doc + " (id " + id + ") has the float vector of id " + owner);
          }
        }
        ByteVectorValues bytes = leaf.getByteVectorValues("byte");
        it = bytes.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          int id = storedFields.document(doc).getField("idnum").numericValue().intValue();
          int owner = bytes.vectorValue(it.index())[0];
          if (owner != id) {
            wrong.add("doc " + doc + " (id " + id + ") has the byte vector of id " + owner);
          }
        }
      }
    }
    return wrong;
  }

  /** Control: without a reorder, the same addIndexes call keeps every vector on its document. */
  public void testNoReorder() throws IOException {
    List<String> wrong = addIndexesAndFindWrongVectors(new LogDocMergePolicy(), false);
    assertTrue(wrong.toString(), wrong.isEmpty());
  }

  public void testIdentityReorder() throws IOException {
    List<String> wrong = addIndexesAndFindWrongVectors(identityReorderOnAddIndexes(), false);
    assertTrue(wrong.size() + " wrong: " + wrong, wrong.isEmpty());
  }

  /** The vector of a document deleted before addIndexes must not reappear on a live document. */
  public void testIdentityReorderWithDeletedDocument() throws IOException {
    List<String> wrong = addIndexesAndFindWrongVectors(identityReorderOnAddIndexes(), true);
    assertTrue(wrong.size() + " wrong: " + wrong, wrong.isEmpty());
  }
}
