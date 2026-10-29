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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMemoryIndexStoredFieldVisitorCalls extends LuceneTestCase {

  public void testNeedsFieldOncePerStoredValue() throws Exception {
    Document doc = new Document();
    doc.add(new TextField("body", "x", Field.Store.NO));
    doc.add(new StoredField("tag", "t1"));
    doc.add(new StoredField("tag", "t2"));
    IndexReader reader =
        MemoryIndex.fromDocument(doc, new StandardAnalyzer()).createSearcher().getIndexReader();
    List<String> asked = new ArrayList<>();
    reader
        .storedFields()
        .document(
            0,
            new StoredFieldVisitor() {
              @Override
              public Status needsField(FieldInfo fieldInfo) {
                asked.add(fieldInfo.name);
                return Status.NO;
              }
            });
    // expected (as for an IndexWriter index): one call per stored value of tag, none for the
    // unstored body; actual: [body, tag]
    assertEquals(List.of("tag", "tag"), asked);
  }
}
