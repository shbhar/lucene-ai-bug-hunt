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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestWildcardEmptyPattern extends LuceneTestCase {

  public void testEmptyPatternMatchesEmptyTerm() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // expected: the pattern "" (no wildcards) matches the indexed term "" (1 hit); actual: 0 hits
        assertEquals(1, newSearcher(r).count(new WildcardQuery(new Term("f", ""))));
      }
    }
  }
}
