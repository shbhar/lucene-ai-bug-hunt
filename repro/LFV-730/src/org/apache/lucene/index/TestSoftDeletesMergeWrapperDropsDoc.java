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
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

public class TestSoftDeletesMergeWrapperDropsDoc extends LuceneTestCase {

  public void testMergeWrapperDropsLiveDoc() throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setSoftDeletesField("soft")
            .setCodec(TestUtil.getDefaultCodec())
            .setMergeScheduler(new SerialMergeScheduler())
            .setMergePolicy(
                new OneMergeWrappingMergePolicy(
                    new LogDocMergePolicy(),
                    merge ->
                        new MergePolicy.OneMerge(merge.segments) {
                          @Override
                          public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                            return dropDoc(reader, "b");
                          }
                        }));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("a"));
      w.addDocument(doc("b"));
      w.commit();
      w.addDocument(doc("c"));
      w.commit();
      // expected: the merge drops b and keeps a, c; actual: it throws (merged softDelCount is -1)
      w.forceMerge(1);
      assertEquals(2, w.getDocStats().numDocs);
    }
  }

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    return doc;
  }

  private static CodecReader dropDoc(CodecReader reader, String id) throws IOException {
    PostingsEnum postings = reader.postings(new Term("id", id));
    int drop = postings == null ? -1 : postings.nextDoc();
    Bits liveDocs = reader.getLiveDocs();
    FixedBitSet live = new FixedBitSet(reader.maxDoc());
    for (int doc = 0; doc < reader.maxDoc(); doc++) {
      if (doc != drop && (liveDocs == null || liveDocs.get(doc))) {
        live.set(doc);
      }
    }
    int numDocs = live.cardinality();
    return new FilterCodecReader(reader) {
      @Override
      public Bits getLiveDocs() {
        return live;
      }

      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public CacheHelper getCoreCacheHelper() {
        return in.getCoreCacheHelper();
      }

      @Override
      public CacheHelper getReaderCacheHelper() {
        return null;
      }
    };
  }
}
