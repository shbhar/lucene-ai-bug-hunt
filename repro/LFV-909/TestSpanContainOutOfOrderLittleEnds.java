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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpanContainingQuery and SpanWithinQuery miss matches when the little query's spans end out of
 * order. Spans are ordered by start and then end, so spanOr("new york city", york) on "new york
 * city" yields [0,3) and then [1,2): the end goes down.
 */
public class TestSpanContainOutOfOrderLittleEnds extends LuceneTestCase {
  private static final String F = "f";

  private static SpanQuery term(String text) {
    return new SpanTermQuery(new Term(F, text));
  }

  private static SpanQuery phrase(String... words) {
    SpanQuery[] clauses = new SpanQuery[words.length];
    for (int i = 0; i < words.length; i++) {
      clauses[i] = term(words[i]);
    }
    return new SpanNearQuery(clauses, 0, true);
  }

  /** Returns "containing=N within=M" for one document holding {@code text}. */
  private String counts(String text, SpanQuery big, SpanQuery little) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              dir,
              newIndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)))) {
        Document doc = new Document();
        doc.add(newTextField(F, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        return "containing="
            + searcher.count(new SpanContainingQuery(big, little))
            + " within="
            + searcher.count(new SpanWithinQuery(big, little));
      }
    }
  }

  /** Adding an OR alternative to the little query must not remove a hit. */
  public void testOrAlternativeRemovesHit() throws Exception {
    SpanQuery big = phrase("new", "york"); // [0,2)
    assertEquals("containing=1 within=1", counts("new york city", big, term("york")));
    SpanQuery little = new SpanOrQuery(phrase("new", "york", "city"), term("york")); // [0,3), [1,2)
    assertEquals("containing=1 within=1", counts("new york city", big, little));
  }

  /** SpanWithinQuery(A, B) and SpanContainingQuery(A, B) must match the same documents. */
  public void testWithinDisagreesWithContaining() throws Exception {
    SpanQuery little = new SpanOrQuery(phrase("new", "york", "city"), term("york"));
    assertEquals("containing=1 within=1", counts("new york city", term("york"), little));
  }

  /** "cake, or apple near pie, inside one sentence": the first sentence contains cake. */
  public void testSentenceScopedOr() throws Exception {
    SpanQuery sentence = new SpanNearQuery(new SpanQuery[] {term("bos"), term("eos")}, 100, true);
    SpanQuery applePie = new SpanNearQuery(new SpanQuery[] {term("apple"), term("pie")}, 5, true);
    SpanQuery little = new SpanOrQuery(applePie, term("cake")); // [1,6), [2,3)
    assertEquals("containing=1 within=1", counts("bos cake eos bos pie eos", sentence, little));
    assertEquals(
        "containing=1 within=1", counts("bos apple cake eos bos pie eos", sentence, little));
  }
}
