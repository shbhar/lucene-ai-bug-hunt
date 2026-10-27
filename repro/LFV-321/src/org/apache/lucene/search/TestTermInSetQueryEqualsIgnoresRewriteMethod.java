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
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * MultiTermQuery#equals compares the rewrite method, but TermInSetQuery overrides equals/hashCode
 * with a version that only compares the terms. TermInSetQuery objects built with rewrite methods that
 * select different documents (a top-terms rewrite) or score differently (SCORING_BOOLEAN_REWRITE)
 * are therefore equal.
 */
public class TestTermInSetQueryEqualsIgnoresRewriteMethod extends LuceneTestCase {

  private static final List<BytesRef> TERMS = List.of(new BytesRef("a"), new BytesRef("b"));

  public void testOtherMultiTermQueriesCompareRewriteMethod() {
    // reference behaviour of the parent class: the rewrite method is part of equality
    Query p1 = new PrefixQuery(new Term("f", "a"), MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE);
    Query p2 = new PrefixQuery(new Term("f", "a"), new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1));
    assertFalse(p1.equals(p2));
  }

  public void testTermInSetQueryWithDifferentRewriteMethodsAreNotEqual() {
    Query q1 = new TermInSetQuery("f", TERMS);
    Query q2 = new TermInSetQuery(new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1), "f", TERMS);
    assertFalse("TermInSetQuery must compare its rewrite method like MultiTermQuery does", q1.equals(q2));
  }

  public void testEqualQueriesMatchDifferentDocuments() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String v : new String[] {"a", "b"}) {
          Document d = new Document();
          d.add(new StringField("f", v, Field.Store.YES));
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query all = new TermInSetQuery("f", TERMS);
        Query top1 = new TermInSetQuery(new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1), "f", TERMS);
        TreeSet<String> h1 = hits(searcher, all), h2 = hits(searcher, top1);
        assertFalse("precondition: different hits " + h1 + " vs " + h2, h1.equals(h2));
        assertFalse("queries with hits " + h1 + " and " + h2 + " must not be equal", all.equals(top1));
      }
    }
  }

  private static TreeSet<String> hits(IndexSearcher searcher, Query q) throws Exception {
    TreeSet<String> out = new TreeSet<>();
    for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
      out.add(searcher.storedFields().document(sd.doc).get("f"));
    }
    return out;
  }
}
