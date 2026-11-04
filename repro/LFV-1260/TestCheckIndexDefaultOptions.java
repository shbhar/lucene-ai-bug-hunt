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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.OutputStream;
import java.io.PrintStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCheckIndexDefaultOptions extends LuceneTestCase {

  public void testDoCheckWithDefaultOptionsReportsBrokenSegment() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new StoredField("f", "v"));
        w.addDocument(doc);
      }
      dir.deleteFile("_0.fdt");
      CheckIndex.Options opts = new CheckIndex.Options();
      opts.setOut(new PrintStream(OutputStream.nullOutputStream(), false, UTF_8));
      try (CheckIndex checker = new CheckIndex(dir)) {
        // expected 1 (segment _0 lacks _0.fdt), actual 0: an empty onlySegments checks no segment
        assertEquals(1, checker.doCheck(opts));
      }
    }
  }
}
