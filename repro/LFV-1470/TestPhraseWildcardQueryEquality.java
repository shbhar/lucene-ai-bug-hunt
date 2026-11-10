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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPhraseWildcardQueryEquality extends LuceneTestCase {

  private static Query phrase(int maxMultiTermExpansions) {
    return new PhraseWildcardQuery.Builder("f", maxMultiTermExpansions)
        .addTerm(new Term("f", "a"))
        .addMultiTerm(new PrefixQuery(new Term("f", "b")))
        .build();
  }

  public void testExpansionLimitIsPartOfEquality() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String text : new String[] {"a bx", "a by"}) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query wide = phrase(100);
        Query narrow = phrase(1);
        assertEquals(2, searcher.count(wide));
        assertEquals(1, searcher.count(narrow));
        // expected 1 (wide matches 2, narrow 1); actual 0: wide.equals(narrow), so rewrite yields
        // MatchNoDocsQuery
        assertEquals(
            1,
            searcher.count(
                new BooleanQuery.Builder()
                    .add(wide, Occur.FILTER)
                    .add(narrow, Occur.MUST_NOT)
                    .build()));
      }
    }
  }
}
