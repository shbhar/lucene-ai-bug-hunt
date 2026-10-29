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
package org.apache.lucene.classification;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBooleanPerceptronBatchSize extends LuceneTestCase {

  public void testUpdateOfSecondDocumentInBatchIsLost() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    FieldType textType = new FieldType(TextField.TYPE_STORED);
    textType.setStoreTermVectors(true);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String text : new String[] {"bb", "aa aa"}) {
          Document doc = new Document();
          doc.add(new Field("text", text, textType));
          doc.add(new StringField("cls", "false", Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BooleanPerceptronClassifier online =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, 1.5, "cls", "text");
        BooleanPerceptronClassifier batched =
            new BooleanPerceptronClassifier(reader, analyzer, null, 2, 1.5, "cls", "text");
        assertEquals(Boolean.FALSE, online.assignClass("aa aa").assignedClass());
        // expected false (as with batchSize 1), actual true: the 'aa aa' update is dropped
        assertEquals(Boolean.FALSE, batched.assignClass("aa aa").assignedClass());
      }
    }
  }
}
