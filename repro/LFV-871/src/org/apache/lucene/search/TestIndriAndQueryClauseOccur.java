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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
 * IndriAndQuery takes BooleanClauses but its scorer ignores their Occur: every clause becomes a
 * matching disjunct (IndriAndWeight#getScorer, IndriDisjunctionScorer). A MUST_NOT clause therefore
 * adds the documents it should exclude, FILTER and MUST clauses do not restrict, and
 * IndriAndWeight#explain, which does read Occur (c.isRequired()), disagrees with the hits. Public API
 * only.
 *
 * <p>Docs: 0 {f=a, acl=x}, 1 {f=a, acl=y}, 2 {f=a, f=secret, acl=x}, 3 {f=b, acl=x}.
 */
public class TestIndriAndQueryClauseOccur extends LuceneTestCase {

  private static TermQuery tq(String f, String v) {
    return new TermQuery(new Term(f, v));
  }

  private static List<BooleanClause> clauses(Object... occurAndQuery) {
    List<BooleanClause> l = new ArrayList<>();
    for (int i = 0; i < occurAndQuery.length; i += 2) {
      l.add(new BooleanClause((Query) occurAndQuery[i + 1], (Occur) occurAndQuery[i]));
    }
    return l;
  }

  private static Set<Integer> hits(IndexSearcher s, Query q) throws Exception {
    Set<Integer> out = new TreeSet<>();
    for (ScoreDoc sd : s.search(q, 10).scoreDocs) {
      out.add(sd.doc);
    }
    return out;
  }

  public void testOccurIsHonoured() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        String[][] docs = {{"a", "x"}, {"a", "y"}, {"a secret", "x"}, {"b", "x"}};
        for (String[] d : docs) {
          Document doc = new Document();
          for (String v : d[0].split(" ")) {
            doc.add(new StringField("f", v, Field.Store.NO));
          }
          doc.add(new StringField("acl", d[1], Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        StringBuilder report = new StringBuilder();
        List<List<BooleanClause>> cases =
            List.of(
                clauses(Occur.MUST_NOT, tq("f", "secret")),
                clauses(Occur.SHOULD, tq("f", "a"), Occur.MUST_NOT, tq("f", "secret")),
                clauses(Occur.MUST, tq("f", "a"), Occur.FILTER, tq("acl", "x")));
        for (List<BooleanClause> cs : cases) {
          BooleanQuery.Builder b = new BooleanQuery.Builder();
          cs.forEach(b::add);
          Set<Integer> expected = hits(searcher, b.build());
          Set<Integer> actual = hits(searcher, new IndriAndQuery(cs));
          if (actual.equals(expected) == false) {
            report
                .append("\n  ")
                .append(cs)
                .append(": IndriAndQuery hits ")
                .append(actual)
                .append(", BooleanClause.Occur semantics (BooleanQuery) ")
                .append(expected);
          }
        }
        // explain reads Occur, the scorer does not: hit 3 of [MUST f:a, SHOULD f:b]
        Query mustShould = new IndriAndQuery(clauses(Occur.MUST, tq("f", "a"), Occur.SHOULD, tq("f", "b")));
        boolean hit3 = hits(searcher, mustShould).contains(3);
        boolean explained3 = searcher.explain(mustShould, 3).isMatch();
        if (hit3 != explained3) {
          report
              .append("\n  ")
              .append(mustShould)
              .append(": doc 3 is a hit=")
              .append(hit3)
              .append(" but explain isMatch=")
              .append(explained3);
        }
        assertEquals(
            "MUST/FILTER clauses must match, MUST_NOT clauses must not; explain must agree with hits:",
            "",
            report.toString());
      }
    }
  }
}
