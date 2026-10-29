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

import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestCachingNaiveBayesEmptyClassValue extends LuceneTestCase {

  public void testSameClassesAsSimpleNaiveBayes() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc("apple banana", "fruit"));
        w.addDocument(doc("car truck", "vehicle"));
        w.addDocument(doc("car bus", ""));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Classifier<BytesRef> parent =
            new SimpleNaiveBayesClassifier(reader, analyzer, null, "cat", "text");
        Classifier<BytesRef> caching =
            new CachingNaiveBayesClassifier(reader, analyzer, null, "cat", "text");
        // expected the parent's classes ['fruit', 'vehicle'], actual ['', 'fruit', 'vehicle']
        assertEquals(
            "CachingNaiveBayesClassifier.getClasses(\"car\")",
            classes(parent.getClasses("car")),
            classes(caching.getClasses("car")));
      }
    }
  }

  private static Document doc(String text, String cls) {
    Document doc = new Document();
    doc.add(new TextField("text", text, Field.Store.NO));
    doc.add(new StringField("cat", cls, Field.Store.NO));
    return doc;
  }

  private static TreeSet<String> classes(List<ClassificationResult<BytesRef>> results) {
    TreeSet<String> classes = new TreeSet<>();
    for (ClassificationResult<BytesRef> r : results) {
      classes.add("'" + r.assignedClass().utf8ToString() + "'");
    }
    return classes;
  }
}
