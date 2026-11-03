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
import java.util.concurrent.Executor;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMergedViewTermVectorsClone extends LuceneTestCase {

  public void testCloneWhenOneSegmentHasNoTermVectors() throws Exception {
    FieldType tvType = new FieldType(TextField.TYPE_NOT_STORED);
    tvType.setStoreTermVectors(true);
    MergePolicy mp =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findMerges(CodecReader... readers) {
            MergeSpecification spec = new MergeSpecification();
            spec.add(
                new OneMerge(readers) {
                  @Override
                  public Sorter.DocMap reorder(CodecReader view, Directory dir, Executor executor)
                      throws IOException {
                    // expected: a clone that reads doc 0's term vectors; actual:
                    // NullPointerException
                    assertNotNull(view.getTermVectorsReader().clone().get(0));
                    return null;
                  }
                });
            return spec;
          }
        };
    try (Directory src = newDirectory();
        Directory dest = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
        Document withTv = new Document();
        withTv.add(new Field("f", "a", tvType));
        w.addDocument(withTv);
        w.commit();
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w =
              new IndexWriter(
                  dest,
                  new IndexWriterConfig()
                      .setMergePolicy(mp)
                      .setMergeScheduler(new SerialMergeScheduler()))) {
        w.addIndexes(
            (CodecReader) r.leaves().get(0).reader(), (CodecReader) r.leaves().get(1).reader());
      }
    }
  }
}
