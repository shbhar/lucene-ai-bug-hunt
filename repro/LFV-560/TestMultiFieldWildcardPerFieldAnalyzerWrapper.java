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
package org.apache.lucene.queryparser.flexible.standard;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * StandardQueryParser with setMultiFields: a prefix or wildcard clause without a field should be
 * normalized with the analyzer of each field it is expanded to, like the same clause written with
 * the field, and like fuzzy and plain clauses through the same expansion.
 */
public class TestMultiFieldWildcardPerFieldAnalyzerWrapper extends LuceneTestCase {

  /** Default WhitespaceAnalyzer, "title" lower-cased by StandardAnalyzer; title "Lucene". */
  public void testLowercasingField() throws Exception {
    Map<String, Analyzer> perField = new HashMap<>();
    perField.put("title", new StandardAnalyzer());
    try (Analyzer a = new PerFieldAnalyzerWrapper(new WhitespaceAnalyzer(), perField);
        Directory dir = newDirectory()) {
      index(dir, a, "title", "Lucene");
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        StandardQueryParser qp = new StandardQueryParser(a);
        qp.setMultiFields(new CharSequence[] {"title"});
        // controls: the explicit-field form, and a fuzzy clause through the same expansion
        assertEquals("title:luc* hits=1", run(s, qp, "title:Luc*"));
        assertEquals("title:lucene~1 hits=1", run(s, qp, "Lucene~1"));

        assertEquals(
            "title:luc* hits=1 | title:lu?ene hits=1",
            run(s, qp, "Luc*") + " | " + run(s, qp, "Lu?ene"));
      }
    }
  }

  /** The PerFieldAnalyzerWrapper javadoc example: default StandardAnalyzer, keyword name fields. */
  public void testKeywordFields() throws Exception {
    Map<String, Analyzer> perField = new HashMap<>();
    perField.put("firstname", new KeywordAnalyzer());
    perField.put("lastname", new KeywordAnalyzer());
    try (Analyzer a = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), perField);
        Directory dir = newDirectory()) {
      index(dir, a, "firstname", "John", "lastname", "Smith");
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        StandardQueryParser qp = new StandardQueryParser(a);
        qp.setMultiFields(new CharSequence[] {"firstname", "lastname"});
        // controls
        assertEquals("firstname:Jo* hits=1", run(s, qp, "firstname:Jo*"));
        assertEquals("firstname:John lastname:John hits=1", run(s, qp, "John"));

        assertEquals(
            "firstname:Jo* lastname:Jo* hits=1 | firstname:J?hn lastname:J?hn hits=1",
            run(s, qp, "Jo*") + " | " + run(s, qp, "J?hn"));
      }
    }
  }

  /** The same cause with a per-field map that rejects null keys: parse() throws. */
  public void testNullRejectingPerFieldMap() throws Exception {
    try (Analyzer a =
        new PerFieldAnalyzerWrapper(
            new WhitespaceAnalyzer(), Map.<String, Analyzer>of("title", new StandardAnalyzer()))) {
      StandardQueryParser qp = new StandardQueryParser(a);
      qp.setMultiFields(new CharSequence[] {"title"});
      assertEquals("title:lucene~1", qp.parse("Lucene~1", null).toString()); // control
      assertEquals("title:luc*", qp.parse("Luc*", null).toString());
    }
  }

  private static void index(Directory dir, Analyzer a, String... fieldsAndValues)
      throws Exception {
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
      Document doc = new Document();
      for (int i = 0; i < fieldsAndValues.length; i += 2) {
        doc.add(new TextField(fieldsAndValues[i], fieldsAndValues[i + 1], Field.Store.NO));
      }
      w.addDocument(doc);
    }
  }

  private static String run(IndexSearcher s, StandardQueryParser qp, String query)
      throws Exception {
    Query q = qp.parse(query, null);
    return q + " hits=" + s.count(q);
  }
}
