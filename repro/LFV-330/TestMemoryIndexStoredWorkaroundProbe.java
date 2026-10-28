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
package org.apache.lucene.index.memory;

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Workaround probe: index with Store.NO and add a separate StoredField under the same name. */
public class TestMemoryIndexStoredWorkaroundProbe extends LuceneTestCase {

  public void testStoredFieldNextToUnstoredField() throws IOException {
    Document doc = new Document();
    doc.add(new StringField("id", "doc-1", Field.Store.NO));
    doc.add(new StoredField("id", "doc-1"));
    doc.add(new IntField("count", 5, Field.Store.NO));
    doc.add(new StoredField("count", 5));
    MemoryIndex mi = MemoryIndex.fromDocument(doc, new StandardAnalyzer());
    IndexSearcher s = mi.createSearcher();
    assertEquals(1, s.count(new TermQuery(new Term("id", "doc-1"))));
    assertEquals(1, s.count(IntField.newExactQuery("count", 5)));
    Document d = s.storedFields().document(0);
    assertEquals("doc-1", d.get("id"));
    assertEquals(5, d.getField("count").numericValue());
  }
}
