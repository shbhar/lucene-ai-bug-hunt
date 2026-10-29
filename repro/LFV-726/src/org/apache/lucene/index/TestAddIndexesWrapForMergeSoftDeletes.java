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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

public class TestAddIndexesWrapForMergeSoftDeletes extends LuceneTestCase {

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    return doc;
  }

  /** Imports readers through a OneMerge whose wrapForMerge drops soft-deleted documents. */
  private static final MergePolicy DROP_SOFT_DELETED_ON_ADD_INDEXES =
      new FilterMergePolicy(new LogDocMergePolicy()) {
        @Override
        public MergeSpecification findMerges(CodecReader... readers) {
          MergeSpecification spec = new MergeSpecification();
          spec.add(
              new OneMerge(readers) {
                @Override
                public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                  FixedBitSet live = new FixedBitSet(reader.maxDoc());
                  live.set(0, reader.maxDoc());
                  NumericDocValues soft = reader.getNumericDocValues("soft");
                  for (int d = soft.nextDoc();
                      d != DocIdSetIterator.NO_MORE_DOCS;
                      d = soft.nextDoc()) {
                    live.clear(d);
                  }
                  return new FilterCodecReader(reader) {
                    @Override
                    public Bits getLiveDocs() {
                      return live;
                    }

                    @Override
                    public int numDocs() {
                      return live.cardinality();
                    }

                    @Override
                    public CacheHelper getCoreCacheHelper() {
                      return null;
                    }

                    @Override
                    public CacheHelper getReaderCacheHelper() {
                      return null;
                    }
                  };
                }
              });
          return spec;
        }
      };

  public void testSoftDeleteCountAfterAddIndexes() throws IOException {
    try (Directory src = new ByteBuffersDirectory();
        Directory dst = new ByteBuffersDirectory()) {
      IndexWriterConfig srcConfig =
          new IndexWriterConfig()
              .setSoftDeletesField("soft")
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(src, srcConfig)) {
        w.addDocument(doc("a"));
        w.softUpdateDocument(new Term("id", "a"), doc("c"), new NumericDocValuesField("soft", 1));
      }
      IndexWriterConfig dstConfig =
          new IndexWriterConfig()
              .setSoftDeletesField("soft")
              .setMergePolicy(DROP_SOFT_DELETED_ON_ADD_INDEXES);
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w = new IndexWriter(dst, dstConfig)) {
        w.addIndexes((CodecReader) r.leaves().get(0).reader());
        w.commit();
        // expected 1 (c; the wrapper dropped soft-deleted a); actual 0: a's soft delete is kept
        assertEquals("DocStats.numDocs after addIndexes", 1, w.getDocStats().numDocs);
      }
    }
  }
}
