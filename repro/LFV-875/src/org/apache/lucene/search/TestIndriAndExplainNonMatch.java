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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndriAndWeight#explain advances its scorer to the requested document but only asserts that it
 * landed there. A document that matches none of the clauses (here: SHOULD clauses only) is then
 * explained as a match carrying the score of the next matching document (assertions disabled), or
 * explain throws AssertionError (assertions enabled). Public API only.
 *
 * <p>Docs: 0 {} (before the first match), 1 {f=a}, 2 {f=a, f=b}, 3 {} (after the last match).
 */
public class TestIndriAndExplainNonMatch extends LuceneTestCase {

  public void testNonMatchingDocumentsAreExplainedAsNonMatches() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        String[][] docs = {{}, {"a"}, {"a", "b"}, {}};
        for (String[] terms : docs) {
          Document doc = new Document();
          for (String t : terms) {
            doc.add(new StringField("f", t, Field.Store.NO));
          }
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        StringBuilder report = new StringBuilder();
        // one clause present in the segment (single sub-scorer) and two clauses (IndriAndScorer)
        Query[] queries = {
          new IndriAndQuery(
              List.of(new BooleanClause(new TermQuery(new Term("f", "a")), Occur.SHOULD))),
          new IndriAndQuery(
              List.of(
                  new BooleanClause(new TermQuery(new Term("f", "a")), Occur.SHOULD),
                  new BooleanClause(new TermQuery(new Term("f", "b")), Occur.SHOULD)))
        };
        for (Query q : queries) {
          TopDocs td = searcher.search(q, 10);
          assertEquals(2, td.scoreDocs.length); // docs 1 and 2
          for (int doc : new int[] {0, 3}) {
            String outcome;
            try {
              Explanation e = searcher.explain(q, doc);
              outcome = e.isMatch() ? "match, value=" + e.getValue() : null;
            } catch (AssertionError e) {
              outcome = "AssertionError from explain";
            }
            if (outcome != null) {
              report.append("\n  ").append(q).append(" doc=").append(doc).append(": ").append(outcome);
            }
          }
        }
        assertEquals(
            "documents 0 and 3 match no clause and are not hits; explain must say no match:",
            "",
            report.toString());
      }
    }
  }

  /**
   * Same defect with a PhraseQuery clause and the default codec: past the last match the scorer is
   * exhausted and explain calls PhraseScorer#score at NO_MORE_DOCS.
   *
   * <p>Docs: 0 "a b" (phrase match), 1 "b a" (both terms, no phrase), 2 "x y z" (a different
   * length, so that the field's norms are not constant).
   */
  public void testPhraseClauseNonMatchAfterLastMatch() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new org.apache.lucene.tests.analysis.MockAnalyzer(random()))
              .setCodec(org.apache.lucene.tests.util.TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String body : new String[] {"a b", "b a", "x y z"}) {
          Document doc = new Document();
          doc.add(new org.apache.lucene.document.TextField("body", body, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q =
            new IndriAndQuery(
                List.of(new BooleanClause(new PhraseQuery("body", "a", "b"), Occur.SHOULD)));
        assertEquals(1, searcher.search(q, 10).scoreDocs.length); // doc 0 only
        StringBuilder report = new StringBuilder();
        for (int doc = 1; doc <= 2; doc++) {
          String outcome;
          try {
            Explanation e = searcher.explain(q, doc);
            outcome = e.isMatch() ? "match, value=" + e.getValue() : null;
          } catch (Throwable t) {
            StackTraceElement[] st = t.getStackTrace();
            outcome = "explain threw " + t + " at " + (st.length > 0 ? st[0] : "?");
            for (StackTraceElement f : st) {
              if (f.getClassName().startsWith("org.apache.lucene.search.Indri")) {
                outcome += " <- " + f;
                break;
              }
            }
          }
          if (outcome != null) {
            report.append("\n  doc=").append(doc).append(": ").append(outcome);
          }
        }
        assertEquals("docs 1 and 2 are not hits; explain must say no match:", "", report.toString());
      }
    }
  }
}
