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
package org.apache.lucene.codecs.lucene103.blocktree;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFailedOpenCleanup extends LuceneTestCase {

  public void testFailedOpenAddsNoSuppressedNPE() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(false);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StringField("f", "a", Field.Store.NO));
      w.addDocument(doc);
    }
    Directory failing =
        new FilterDirectory(dir) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            if (name.endsWith(".tip")) throw new IOException("simulated");
            return super.openInput(name, context);
          }
        };
    IOException e = expectThrows(IOException.class, () -> DirectoryReader.open(failing));
    // expected: the IOException with no suppressed exceptions; actual: a suppressed NPE from close()
    assertEquals(List.of(), List.of(e.getSuppressed()));
    dir.close();
  }
}
