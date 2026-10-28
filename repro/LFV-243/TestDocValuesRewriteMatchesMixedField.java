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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Probe of a second manifestation of the same root cause: a field that has BOTH tokenized postings
 * (TextField) and a raw SortedDocValuesField under the same name (a common "search + sort" layout).
 * DOC_VALUES_REWRITE matches on the doc values, but Weight#matches answers from the postings, so it
 * can be non-null for a non-hit and null for a hit.
 */
public class TestDocValuesRewriteMatchesMixedField extends LuceneTestCase {

  public void testMatchesFollowPostingsNotDocValues() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new StandardAnalyzer()))) {
        String[] titles = {"red apple", "red"};
        for (String t : titles) {
          Document d = new Document();
          d.add(new TextField("title", t, Field.Store.YES));
          d.add(new SortedDocValuesField("title", new BytesRef(t)));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        assertEquals(1, reader.leaves().size());
        LeafReaderContext ctx = reader.leaves().get(0);
        StringBuilder problems = new StringBuilder();
        for (String value : new String[] {"red", "red apple"}) {
          Query q = SortedDocValuesField.newSlowSetQuery("title", List.of(new BytesRef(value)));
          Weight weight =
              searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
          TopDocs td = searcher.search(q, 10);
          assertEquals(1, td.scoreDocs.length);
          int hitDoc = td.scoreDocs[0].doc;
          for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
            Matches m = weight.matches(ctx, doc);
            boolean hit = doc == hitDoc;
            if (hit && m == null) {
              problems.append(" [" + q + ": hit doc " + doc + " has null Matches]");
            } else if (!hit && m != null) {
              problems.append(" [" + q + ": non-hit doc " + doc + " has non-null Matches]");
            }
          }
        }
        assertEquals("Weight#matches disagrees with hits:", "", problems.toString());
      }
    }
  }
}
