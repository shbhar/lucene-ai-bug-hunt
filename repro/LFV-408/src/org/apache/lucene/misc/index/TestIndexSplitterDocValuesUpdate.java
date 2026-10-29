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
package org.apache.lucene.misc.index;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestIndexSplitterDocValuesUpdate extends LuceneTestCase {

  public void testCopySurvivesIndexWriterOpen() throws Exception {
    Path src = createTempDir("src");
    Path dest = createTempDir("dest");
    try (Directory dir = FSDirectory.open(src);
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new StringField("id", "0", Field.Store.NO));
      doc.add(new NumericDocValuesField("price", 10));
      w.addDocument(doc);
      w.commit();
      w.updateNumericDocValue(new Term("id", "0"), "price", 999);
    }
    new IndexSplitter(src).split(dest, new String[] {"_0"});
    try (Directory dir = FSDirectory.open(dest)) {
      new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())).close();
      // expected: the copy still opens and reads price=999; actual: the writer deleted _0_1.fnm
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        NumericDocValues price = reader.leaves().get(0).reader().getNumericDocValues("price");
        assertTrue(price.advanceExact(0));
        assertEquals(999, price.longValue());
      } catch (NoSuchFileException e) {
        throw new AssertionError("expected the copy to open, but got " + e, e);
      }
    }
  }
}
