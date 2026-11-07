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

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

public class TestMergeWrapperKeepsHardDeletedDocs extends LuceneTestCase {

  public void testMergedSegmentStoresEveryDocItCounts() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setSoftDeletesField("soft")
              .setMergeScheduler(new SerialMergeScheduler())
              .setMergePolicy(
                  new OneMergeWrappingMergePolicy(
                      new LogDocMergePolicy(),
                      merge ->
                          new MergePolicy.OneMerge(merge.segments) {
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
                          }));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String id : new String[] {"live", "hard", "softThenHard"}) {
          Document doc = new Document();
          doc.add(new StringField("id", id, Field.Store.YES));
          w.softUpdateDocument(new Term("id", id), doc, new NumericDocValuesField("soft", 1));
        }
        w.updateDocValues(new Term("id", "softThenHard"), new NumericDocValuesField("soft", 1));
        w.commit();
        w.deleteDocuments(new Term("id", "hard"), new Term("id", "softThenHard"));
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        Bits live = leaf.getLiveDocs();
        Set<String> ids = new HashSet<>();
        for (int doc = 0; doc < leaf.maxDoc(); doc++) {
          if (live == null || live.get(doc)) {
            ids.add(leaf.storedFields().document(doc).get("id"));
          }
        }
        // expected: every counted live doc is stored (numDocs == ids read);
        // actual: maxDoc 2 but 1 doc stored
        assertTrue(ids.contains("live"));
        assertEquals(r.numDocs(), ids.size());
      }
    }
  }
}
