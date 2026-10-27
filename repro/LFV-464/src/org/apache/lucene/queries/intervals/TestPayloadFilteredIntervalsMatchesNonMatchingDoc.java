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
package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.IdentityEncoder;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Weight#matches must return null for a document that does not match. A payload-filtered term
 * source returns a (non-null, empty) matches iterator for every document that contains the term,
 * even when the filter rejects every position, so IntervalQuery reports Matches for non-matching
 * documents and a BooleanQuery that excludes such a clause reports no Matches for its hits.
 */
public class TestPayloadFilteredIntervalsMatchesNonMatchingDoc extends LuceneTestCase {

  public void testMatchesOfDocumentWithOnlyRejectedPayloads() throws IOException {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                t, new DelimitedPayloadTokenFilter(t, '|', new IdentityEncoder()));
          }
        };
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String text : new String[] {"big|adj dog|noun", "big|adj dog|verb"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        LeafReaderContext ctx = searcher.getIndexReader().leaves().get(0);
        Query dogAsNoun =
            new IntervalQuery(
                "f", Intervals.term("dog", p -> p != null && p.utf8ToString().equals("noun")));

        // doc 1 ('dog' only as a verb) does not match the payload-filtered query ...
        TopDocs hits = searcher.search(dogAsNoun, 10);
        assertEquals(1, hits.scoreDocs.length);
        assertEquals(0, hits.scoreDocs[0].doc);
        Weight w1 =
            searcher.createWeight(searcher.rewrite(dogAsNoun), ScoreMode.COMPLETE_NO_SCORES, 1f);
        StringBuilder failures = new StringBuilder();
        if (w1.matches(ctx, 1) != null) {
          failures.append("\n  ").append(dogAsNoun).append(": Matches for non-matching doc 1");
        }

        // ... so excluding it keeps doc 1 as a hit, which must then have Matches
        Query bigNotNoun =
            new BooleanQuery.Builder()
                .add(new IntervalQuery("f", Intervals.term("big")), BooleanClause.Occur.MUST)
                .add(dogAsNoun, BooleanClause.Occur.MUST_NOT)
                .build();
        TopDocs hits2 = searcher.search(bigNotNoun, 10);
        assertEquals(1, hits2.scoreDocs.length);
        assertEquals(1, hits2.scoreDocs[0].doc);
        Weight w2 =
            searcher.createWeight(searcher.rewrite(bigNotNoun), ScoreMode.COMPLETE_NO_SCORES, 1f);
        if (w2.matches(ctx, 1) == null) {
          failures.append("\n  ").append(bigNotNoun).append(": no Matches for its hit doc 1");
        }
        assertEquals("Weight#matches disagrees with the hits:" + failures, 0, failures.length());
      }
    }
  }
}
