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
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.StoredFieldDataInput;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** A StoredField built from a StoredFieldDataInput (GITHUB#14213) is silently not stored. */
public class TestMemoryIndexStoredDataInput extends LuceneTestCase {

  public void testDataInputStoredField() throws IOException {
    byte[] bytes = {1, 2, 3};
    StoredField field =
        new StoredField("blob", new StoredFieldDataInput(new ByteArrayDataInput(bytes), bytes.length));
    Document d =
        MemoryIndex.fromDocument(List.of(field), new StandardAnalyzer())
            .createSearcher()
            .storedFields()
            .document(0);
    assertNotNull("stored DataInput value is missing", d.getField("blob"));
    assertEquals(new BytesRef(bytes), d.getField("blob").binaryValue());
  }
}
