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
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBM25NBUntrainedClass extends LuceneTestCase {

  public void testClassExcludedByTrainingQueryIsNotAssigned() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        String[][] docs = {
          {"apple", "fruit", "train"}, {"car", "vehicle", "train"}, {"zzz", "heldout", "test"}
        };
        for (String[] d : docs) {
          Document doc = new Document();
          doc.add(new TextField("text", d[0], Field.Store.NO));
          doc.add(new StringField("cls", d[1], Field.Store.NO));
          doc.add(new StringField("split", d[2], Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        TermQuery trainingDocs = new TermQuery(new Term("split", "train"));
        BM25NBClassifier classifier =
            new BM25NBClassifier(reader, analyzer, trainingDocs, "cls", "text");
        // Expected: "fruit"; actual: "heldout", whose only document the training query excludes.
        assertEquals("fruit", classifier.assignClass("apple").assignedClass().utf8ToString());
      }
    }
  }
}
