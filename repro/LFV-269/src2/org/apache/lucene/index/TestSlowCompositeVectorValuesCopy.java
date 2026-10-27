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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Same-package check (as TestGetVectorCountCodecWrappers does) that copy() of the merged vector
 * values of SlowCompositeCodecReaderWrapper works when one of the wrapped readers has no vectors
 * for the field, for the float and byte encodings (release-compatible variant: no float16 API before 11.0). KnnVectorValues#copy javadoc: "Creates a new copy of
 * this KnnVectorValues".
 */
public class TestSlowCompositeVectorValuesCopy extends LuceneTestCase {

  private void check(String field, boolean withVectorInSecond) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (int seg = 0; seg < 2; seg++) {
          Document doc = new Document();
          doc.add(new StringField("id", "d" + seg, Field.Store.NO));
          if (seg == 0 || withVectorInSecond) {
            switch (field) {
              case "f" -> doc.add(new KnnFloatVectorField("f", new float[] {1, 2}));
              case "b" -> doc.add(new KnnByteVectorField("b", new byte[] {1, 2}));
              default -> throw new AssertionError();
            }
          }
          w.addDocument(doc);
          w.commit();
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.leaves().size());
        List<CodecReader> leaves = new ArrayList<>();
        for (LeafReaderContext ctx : r.leaves()) {
          leaves.add((CodecReader) ctx.reader());
        }
        CodecReader composite = SlowCompositeCodecReaderWrapper.wrap(leaves);
        KnnVectorValues values =
            switch (field) {
              case "f" -> composite.getFloatVectorValues(field);
              default -> composite.getByteVectorValues(field);
            };
        KnnVectorValues copy = values.copy();
        assertEquals(values.size(), copy.size());
      }
    }
  }

  public void testControlFloatAllLeavesHaveVectors() throws IOException {
    check("f", true);
  }

  public void testFloatCopyWithLeafWithoutVectors() throws IOException {
    check("f", false);
  }

  public void testByteCopyWithLeafWithoutVectors() throws IOException {
    check("b", false);
  }
}
