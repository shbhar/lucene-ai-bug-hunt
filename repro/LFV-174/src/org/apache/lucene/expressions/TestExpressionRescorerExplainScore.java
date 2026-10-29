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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestExpressionRescorerExplainScore extends LuceneTestCase {

  public void testScoreDetailIsFirstPassScore() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("popularity", 7));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        SimpleBindings bindings = new SimpleBindings();
        bindings.add("_score", DoubleValuesSource.SCORES);
        bindings.add("popularity", DoubleValuesSource.fromIntField("popularity"));
        Rescorer rescorer =
            JavascriptCompiler.compile("_score * 100 + popularity").getRescorer(bindings);
        Explanation firstPass = searcher.explain(new MatchAllDocsQuery(), 0);
        Explanation expl = rescorer.explain(searcher, firstPass, 0);
        // expected: the _score detail is the first-pass score 1.0; actual: 0.0 (SortRescorer node)
        assertEquals(
            expl.toString(),
            firstPass.getValue().doubleValue(),
            expl.getDetails()[0].getValue().doubleValue(),
            0.0);
      }
    }
  }
}
