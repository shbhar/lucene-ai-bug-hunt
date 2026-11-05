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

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

public class TestAddIndexesWrappedDocCountOverflow extends LuceneTestCase {

  public void testRevivingWrapperAboveMaxDocs() throws Exception {
    Sort sort = new Sort(new SortField("k", SortField.Type.LONG));
    MergePolicy revivingPolicy =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findMerges(CodecReader... readers) {
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(readers) {
                  @Override
                  public CodecReader wrapForMerge(CodecReader reader) {
                    return new FilterCodecReader(reader) {
                      @Override
                      public Bits getLiveDocs() {
                        return null;
                      }

                      @Override
                      public int numDocs() {
                        return maxDoc();
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
    try (Directory src = newDirectory();
        Directory dest = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig().setIndexSort(sort))) {
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w =
              new IndexWriter(
                  dest,
                  new IndexWriterConfig()
                      .setIndexSort(sort)
                      .setMergePolicy(revivingPolicy)
                      .setMergeScheduler(new SerialMergeScheduler()))) {
        CodecReader padded =
            new FilterCodecReader((CodecReader) getOnlyLeafReader(r)) {
              @Override
              public int maxDoc() {
                return 1_100_000_000;
              }

              @Override
              public int numDocs() {
                return 1;
              }

              @Override
              public Bits getLiveDocs() {
                return new Bits() {
                  @Override
                  public boolean get(int index) {
                    return index == 0;
                  }

                  @Override
                  public int length() {
                    return 1_100_000_000;
                  }
                };
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
        // Expected: IllegalArgumentException (2.2e9 revived docs > MAX_DOCS); actual: adds 0 docs.
        expectThrows(IllegalArgumentException.class, () -> w.addIndexes(padded, padded));
      }
    }
  }
}
