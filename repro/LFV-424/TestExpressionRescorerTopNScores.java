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
package org.apache.lucene.expressions;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCaseJupiter;
import org.junit.jupiter.api.Test;

/**
 * Expression#getRescorer (ExpressionRescorer extends SortRescorer): keeping fewer hits (topN
 * smaller than the number of first-pass hits) must only truncate the result, not change a kept
 * hit's score.
 */
public class TestExpressionRescorerTopNScores extends LuceneTestCaseJupiter {

  @Test
  public void testTopNSmallerThanHits() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        // doc 0: "a" once, popularity 1; doc 1: "a" three times, popularity 2
        Document d0 = new Document();
        d0.add(newTextField("body", "a b c d", Field.Store.NO));
        d0.add(new NumericDocValuesField("popularity", 1));
        w.addDocument(d0);
        Document d1 = new Document();
        d1.add(newTextField("body", "a a a", Field.Store.NO));
        d1.add(new NumericDocValuesField("popularity", 2));
        w.addDocument(d1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs firstPass = searcher.search(new TermQuery(new Term("body", "a")), 10);
        assertEquals(2, firstPass.scoreDocs.length);

        Expression e = JavascriptCompiler.compile("popularity");
        SimpleBindings bindings = new SimpleBindings();
        bindings.add("popularity", DoubleValuesSource.fromLongField("popularity"));
        Rescorer rescorer = e.getRescorer(bindings);

        TopDocs all = rescorer.rescore(searcher, firstPass, 2);
        TopDocs top1 = rescorer.rescore(searcher, firstPass, 1);
        assertEquals(1, all.scoreDocs[0].doc); // the more popular doc comes first
        assertEquals(1, top1.scoreDocs.length);
        assertEquals(all.scoreDocs[0].doc, top1.scoreDocs[0].doc);
        assertEquals(
            "score of doc " + top1.scoreDocs[0].doc,
            all.scoreDocs[0].score,
            top1.scoreDocs[0].score,
            0f);
      }
    }
  }
}
