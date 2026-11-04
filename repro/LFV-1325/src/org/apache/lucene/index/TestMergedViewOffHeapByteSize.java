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
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestMergedViewOffHeapByteSize extends LuceneTestCase {

  public void testOffHeapByteSizeWithSegmentWithoutVectors() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {1, 2}));
        w.addDocument(doc);
        w.commit();
        w.addDocument(new Document());
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        List<CodecReader> leaves =
            reader.leaves().stream().map(ctx -> (CodecReader) ctx.reader()).toList();
        CodecReader view = SlowCompositeCodecReaderWrapper.wrap(leaves);
        FieldInfo v = view.getFieldInfos().fieldInfo("v");
        // expected: the vector segment's size map; actual: NullPointerException (null sub-reader)
        assertEquals(
            leaves.get(0).getVectorReader().getOffHeapByteSize(v),
            view.getVectorReader().getOffHeapByteSize(v));
      }
    }
  }
}
