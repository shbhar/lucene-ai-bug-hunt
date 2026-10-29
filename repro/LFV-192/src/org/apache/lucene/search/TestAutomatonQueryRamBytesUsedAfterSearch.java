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
package org.apache.lucene.search;

import java.io.IOException;
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

public class TestAutomatonQueryRamBytesUsedAfterSearch extends LuceneTestCase {

  public void testRamBytesUsedCoversCompiledAutomatonAfterSearch() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, config)) {
        for (int i = 0; i < 1024; i++) { // every 10-character string over {a, b}
          String bits = Integer.toBinaryString(1024 + i).substring(1);
          String term = bits.replace('0', 'a').replace('1', 'b');
          Document doc = new Document();
          doc.add(new StringField("f", term, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        WildcardQuery q = new WildcardQuery(new Term("f", "*a?????????"));
        new IndexSearcher(reader).count(q);
        long compiled = q.getCompiled().ramBytesUsed();
        assertTrue(
            "expected WildcardQuery.ramBytesUsed() >= its compiled automaton's "
                + compiled
                + " after one search, but was "
                + q.ramBytesUsed(),
            q.ramBytesUsed() >= compiled);
      }
    }
  }
}
