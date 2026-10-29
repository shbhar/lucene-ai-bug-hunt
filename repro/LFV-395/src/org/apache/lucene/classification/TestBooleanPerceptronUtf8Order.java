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

public class TestBooleanPerceptronUtf8Order extends LuceneTestCase {

  public void testHighBmpAndSupplementaryTerms() throws Exception {
    String p = "\uF900";
    String n = new String(Character.toChars(0x1D400));
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(true);
    try (Directory dir = new ByteBuffersDirectory();
        Analyzer analyzer = new StandardAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        String[][] docs = {{p + " " + p + " " + p, "true"}, {n, "false"}};
        for (String[] d : docs) {
          Document doc = new Document();
          doc.add(new Field("text", d[0], ft));
          doc.add(new StringField("cls", d[1], Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BooleanPerceptronClassifier classifier =
            new BooleanPerceptronClassifier(reader, analyzer, null, 1, null, "cls", "text");
        // expected true, as with ASCII terms; actual: FSTCompiler AssertionError (-ea), false (-da)
        assertEquals(Boolean.TRUE, classifier.assignClass(p).assignedClass());
      }
    }
  }
}
