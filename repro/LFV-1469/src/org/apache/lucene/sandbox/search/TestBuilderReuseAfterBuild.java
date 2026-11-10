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
package org.apache.lucene.sandbox.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.sandbox.document.IntPointMultiRangeBuilder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBuilderReuseAfterBuild extends LuceneTestCase {

  // Expected: adding to a builder after build() leaves the earlier query unchanged; actual: it changes.
  public void testMultiRangeBuilder() throws Exception {
    IntPointMultiRangeBuilder builder = new IntPointMultiRangeBuilder("pt", 1);
    builder.add(new int[] {0}, new int[] {0});
    Query q = builder.build();
    builder.add(new int[] {3}, new int[] {3});
    Document doc = new Document();
    doc.add(new IntPoint("pt", 3));
    assertEquals(q.toString(), 0, count(doc, q));
  }

  public void testPhraseWildcardBuilder() throws Exception {
    PhraseWildcardQuery.Builder builder = new PhraseWildcardQuery.Builder("body", 10);
    builder.addTerm(new Term("body", "x")).addMultiTerm(new PrefixQuery(new Term("body", "b")));
    Query q = builder.build();
    builder.addTerm(new Term("body", "z"));
    Document doc = new Document();
    doc.add(new TextField("body", "x bb", Field.Store.NO));
    assertEquals(q.toString(), 1, count(doc, q));
  }

  private static int count(Document doc, Query q) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(q);
      }
    }
  }
}
