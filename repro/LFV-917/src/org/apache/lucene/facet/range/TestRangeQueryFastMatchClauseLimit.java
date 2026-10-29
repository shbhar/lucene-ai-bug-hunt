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
package org.apache.lucene.facet.range;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRangeQueryFastMatchClauseLimit extends LuceneTestCase {

  public void testFastMatchQueryCountsTowardsClauseLimit() throws IOException {
    BooleanQuery.Builder fastMatch = new BooleanQuery.Builder();
    for (int i = 0; i < 2; i++) {
      BooleanQuery.Builder terms = new BooleanQuery.Builder();
      for (int j = 0; j < 600; j++) {
        terms.add(new TermQuery(new Term("f", i + "_" + j)), Occur.SHOULD);
      }
      fastMatch.add(terms.build(), Occur.MUST);
    }
    Query query =
        new LongRange("r", 0, true, 10, true)
            .getQuery(fastMatch.build(), LongValuesSource.fromLongField("n"));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(new Document());
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // expected: TooManyNestedClauses (1200 fast-match clauses > 1024); actual: no exception
        expectThrows(IndexSearcher.TooManyNestedClauses.class, () -> searcher.search(query, 1));
      }
    }
  }
}
