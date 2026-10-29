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
package org.apache.lucene.codecs.perfield;

import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestPerFieldPostingsStaleAttributes extends LuceneTestCase {

  public void testMergeAfterSimpleTextSegmentDroppedTheField() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        doc.add(new StringField("g", "y", Field.Store.NO));
        w.addDocument(doc);
        w.addDocument(new Document());
      }
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(new SimpleTextCodec()))) {
        w.deleteDocuments(new Term("g", "y"));
        w.forceMerge(1);
      }
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        w.addDocument(new Document());
        w.forceMerge(1);
      }
      // expected: the committed index opens with 2 docs; actual: CorruptIndexException (no .psm file)
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
      }
    }
  }
}
