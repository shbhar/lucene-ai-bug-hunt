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
package org.apache.lucene.codecs.uniformsplit;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestUniformSplitLargeDeltaFlush extends LuceneTestCase {

  public void testCommitThreeTermsWithTarget4Delta2() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(
                TestUtil.alwaysPostingsFormat(
                    new UniformSplitPostingsFormat(4, 2, null, null, false)));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StringField("f", "a", Field.Store.NO));
      doc.add(new StringField("f", "b", Field.Store.NO));
      doc.add(new StringField("f", "c", Field.Store.NO));
      w.addDocument(doc);
      try {
        w.commit();
      } catch (Throwable t) {
        // Expected: commit succeeds, (4, 2) meets the documented 0 <= deltaNumLines < targetNumBlockLines; actual: it throws.
        throw new AssertionError("commit with UniformSplitPostingsFormat(4, 2) threw " + t, t);
      }
    }
  }
}
