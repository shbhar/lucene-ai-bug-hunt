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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRejectedFullFlushMerge extends LuceneTestCase {

  public void testCommitThrowsOnOverlappingFullFlushMerges() throws Exception {
    MergePolicy overlapping =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findFullFlushMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
            MergeSpecification spec = new MergeSpecification();
            spec.add(new OneMerge(List.of(infos.info(0), infos.info(1))));
            spec.add(new OneMerge(List.of(infos.info(1), infos.info(2))));
            return spec;
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig().setMergePolicy(overlapping).setCommitOnClose(false);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 3; i++) {
        w.addDocument(new Document());
        w.flush();
      }
      // expected (MergePolicy#findFullFlushMerges javadoc): IllegalStateException; actual: none
      expectThrows(IllegalStateException.class, w::commit);
    }
  }
}
