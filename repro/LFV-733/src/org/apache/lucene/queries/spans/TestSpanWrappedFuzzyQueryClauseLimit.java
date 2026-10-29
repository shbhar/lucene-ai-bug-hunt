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
package org.apache.lucene.queries.spans;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpanWrappedFuzzyQueryClauseLimit extends LuceneTestCase {

  public void testSpanWrappedFuzzyQueryIsCappedAtMaxClauseCount() throws Exception {
    int saved = IndexSearcher.getMaxClauseCount();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String t : new String[] {"abca", "abcb", "abce", "abcf"}) {
          Document doc = new Document();
          doc.add(new TextField("f", t, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      IndexSearcher.setMaxClauseCount(2);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        FuzzyQuery fuzzy = new FuzzyQuery(new Term("f", "abcd"));
        int plain = s.count(fuzzy);
        try {
          assertEquals(plain, s.count(new SpanMultiTermQueryWrapper<>(fuzzy)));
        } catch (IndexSearcher.TooManyClauses e) {
          // expected: capped at maxClauseCount like the plain FuzzyQuery; actual: TooManyNestedClauses
          throw new AssertionError("expected " + plain + " hits like the plain FuzzyQuery, got " + e, e);
        }
      }
    } finally {
      IndexSearcher.setMaxClauseCount(saved);
    }
  }
}
