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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestOpenIfChangedAfterDocValuesUpdateAndFlush extends LuceneTestCase {

  public void testNumericUpdateThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      doc.add(new NumericDocValuesField("val", 1));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.updateNumericDocValue(new Term("id", "1"), "val", 2);
        w.flush();
        // expected: a new reader that sees val=2; actual: null, so the update stays invisible
        DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
        assertNotNull("expected a new reader after a doc-values update, but got null", r2);
        r2.close();
      }
    }
  }
}
