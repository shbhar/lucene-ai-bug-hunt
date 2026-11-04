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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFieldUpdatesTempReaderCloseFailure extends LuceneTestCase {

  public void testRetryCommitAfterTemporaryReaderCloseFailure() throws Exception {
    AtomicBoolean failClose = new AtomicBoolean();
    IndexWriter[] writer = new IndexWriter[1];
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergeScheduler(new SerialMergeScheduler())
            .setMergePolicy(
                new FilterMergePolicy(NoMergePolicy.INSTANCE) {
                  @Override
                  public MergeSpecification findForcedMerges(
                      SegmentInfos infos,
                      int maxSegmentCount,
                      Map<SegmentCommitInfo, Boolean> segmentsToMerge,
                      MergeContext mergeContext) {
                    if (infos.size() < 2) {
                      return null;
                    }
                    MergeSpecification spec = new MergeSpecification();
                    spec.add(
                        new OneMerge(new ArrayList<>(infos.asList())) {
                          boolean updated;

                          @Override
                          public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                            if (updated == false) {
                              updated = true;
                              writer[0].updateNumericDocValue(new Term("id", "0"), "n", 100);
                              writer[0].flush();
                            }
                            return reader;
                          }
                        });
                    return spec;
                  }
                });
    try (MockDirectoryWrapper dir =
        new MockDirectoryWrapper(random(), new ByteBuffersDirectory())) {
      dir.failOn(
          new MockDirectoryWrapper.Failure() {
            @Override
            public void eval(MockDirectoryWrapper d) throws IOException {
              if (failClose.get()
                  && callStackContains(ReadersAndUpdates.class, "writeFieldUpdates")
                  && callStackContains(SegmentReader.class, "doClose")) {
                failClose.set(false);
                throw new IOException("fake close failure");
              }
            }
          });
      IndexWriter w = writer[0] = new IndexWriter(dir, iwc);
      try {
        for (int id = 0; id < 2; id++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
          doc.add(new NumericDocValuesField("n", id));
          w.addDocument(doc);
          w.commit();
        }
        w.forceMerge(1);
        failClose.set(true);
        assertEquals("fake close failure", expectThrows(IOException.class, w::commit).getMessage());
        // expected: the retried commit succeeds; actual: NoSuchFileException: _2_1.fnm
        w.commit();
      } finally {
        w.rollback();
      }
    }
  }
}
