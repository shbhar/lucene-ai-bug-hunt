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

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Scope probe: one escaped literal a\:b under several composite fn: functions. Doc 0 = "a:b c",
 * doc 1 = "a\:b c". Each query should hit doc 0 (as a\:b does outside fn:). Fails if any query
 * does not; prints every parse and hit set.
 */
public class TestIntervalEscapeScopeProbe extends LuceneTestCase {

  public void testCompositeFunctions() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    String[] queries = {
      "a\\:b",
      "fn:or(a\\:b)",
      "fn:ordered(a\\:b c)",
      "fn:unordered(a\\:b c)",
      "fn:phrase(a\\:b c)",
      "fn:atleast(1 a\\:b zz)",
      "fn:maxwidth(5 fn:or(a\\:b))",
      "fn:containedBy(a\\:b fn:ordered(a\\:b c))",
      "fn:or(fn:or(a\\:b) zz)",
      "fn:fuzzyTerm(a\\:b 0)",
      "fn:or(\"a\\:b\")"
    };
    StringBuilder wrong = new StringBuilder();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (String text : new String[] {"a:b c", "a\\:b c"}) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        for (String qs : queries) {
          Query q = new StandardQueryParser(a).parse(qs, "body");
          Set<Integer> ids = new TreeSet<>();
          for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
            ids.add(sd.doc);
          }
          System.out.println("SCOPE " + qs + " -> " + q + " hits=" + ids);
          if (ids.equals(Set.of(0)) == false) {
            wrong.append(qs).append(" -> ").append(ids).append("; ");
          }
        }
      }
    }
    assertEquals("queries that do not hit only doc 0", "", wrong.toString());
  }
}
