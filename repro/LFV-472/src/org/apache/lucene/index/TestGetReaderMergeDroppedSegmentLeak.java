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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGetReaderMergeDroppedSegmentLeak extends LuceneTestCase {

  public void testFullyDeletedGetReaderMergeLeavesNoFiles() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(NoMergePolicy.INSTANCE)
              .setMergeScheduler(new SerialMergeScheduler());
      IndexWriter w = new IndexWriter(dir, iwc);
      for (int i = 0; i < 2; i++) {
        w.addDocument(List.of(new StringField("f", "x", Field.Store.NO)));
        w.flush();
      }
      w.getConfig()
          .setMergePolicy(
              new FilterMergePolicy(NoMergePolicy.INSTANCE) {
                @Override
                public MergeSpecification findFullFlushMerges(
                    MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
                  if (trigger != MergeTrigger.GET_READER) {
                    return null;
                  }
                  MergeSpecification spec = new MergeSpecification();
                  spec.add(
                      new OneMerge(infos.asList()) {
                        @Override
                        public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                          w.deleteDocuments(new Term("f", "x"));
                          w.flush();
                          return reader;
                        }
                      });
                  return spec;
                }
              });
      DirectoryReader.open(w).close();
      w.close();
      Set<String> leftover = new TreeSet<>(Arrays.asList(dir.listAll()));
      leftover.removeAll(SegmentInfos.readLatestCommit(dir).files(true));
      // expected: no files outside the last commit after close(); actual: the dropped merged segment's files
      assertEquals(Set.of(), leftover);
    }
  }
}
