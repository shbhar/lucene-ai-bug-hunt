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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The fn:ordered docs say "Source intervals must not overlap" and list fn:ordered(fn:phrase(brown
 * fox) fn:phrase(fox jumps)) as "(Sources overlap, no matches.)". The same must hold when both
 * sources are the same phrase.
 */
public class TestFnOrderedRepeatOverlap extends LuceneTestCase {

  private static final String FIELD = "f";

  private static int count(String queryText, String text) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        StandardQueryParser parser = new StandardQueryParser(analyzer);
        return new IndexSearcher(reader).count(parser.parse(queryText, FIELD));
      }
    }
  }

  public void testSameQuotedTextTwice() throws Exception {
    // controls: the documented example, and an equivalent but unequal second source
    assertEquals(
        0,
        count(
            "fn:ordered(fn:phrase(brown fox) fn:phrase(fox jumps))",
            "the quick brown fox jumps over the lazy dog"));
    assertEquals(0, count("fn:ordered(\"a a\" fn:or(\"a a\" zzz))", "a a a"));
    assertEquals(1, count("fn:ordered(\"a a\" \"a a\")", "a a a a"));
    // affected builds: 1
    assertEquals(0, count("fn:ordered(\"a a\" \"a a\")", "a a a"));
  }

  public void testUnorderedNoOverlapsSamePhraseTwice() throws Exception {
    assertEquals(
        0,
        count(
            "fn:unorderedNoOverlaps(fn:phrase(brown fox) fn:phrase(fox jumps))",
            "the quick brown fox jumps over the lazy dog"));
    // affected builds: 1
    assertEquals(0, count("fn:unorderedNoOverlaps(fn:phrase(a a) fn:phrase(a a))", "a a a"));
  }
}
