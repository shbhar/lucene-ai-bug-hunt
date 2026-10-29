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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestMultiDocValuesNormsAdvance extends LuceneTestCase {

  public void testNormsAdvanceIntoSegmentWithoutNorms() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document withBody = new Document();
      withBody.add(new TextField("body", "text", Field.Store.NO));
      w.addDocument(withBody);
      w.flush();
      w.addDocument(new Document());
      w.flush();
      w.addDocument(withBody);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        NumericDocValues norms = MultiDocValues.getNormValues(reader, "body");
        // expected: advance(1) returns doc 2, the first doc >= 1 with a norm; actual: doc 0
        assertEquals(2, norms.advance(1));
      }
    }
  }
}
