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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/** Release-agnostic main() form of TestExpressionRescorerExplainScore: exits 1 when the bug is present. */
public class ExpressionRescorerExplainScoreMain {
  public static void main(String[] args) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new TextField("body", "contents" + " filler".repeat(i), Field.Store.NO));
        doc.add(new NumericDocValuesField("popularity", 5 + i));
        w.addDocument(doc);
      }
    }
    int bad = 0;
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query query = new TermQuery(new Term("body", "contents"));
      TopDocs firstPass = searcher.search(query, 10);
      Expression expr = JavascriptCompiler.compile("_score * 100 + popularity");
      SimpleBindings bindings = new SimpleBindings();
      bindings.add("_score", DoubleValuesSource.SCORES);
      bindings.add("popularity", DoubleValuesSource.fromIntField("popularity"));
      Rescorer rescorer = expr.getRescorer(bindings);
      for (ScoreDoc hit : rescorer.rescore(searcher, firstPass, 10).scoreDocs) {
        double rescoredValue = (Double) ((FieldDoc) hit).fields[0];
        Explanation expl = rescorer.explain(searcher, searcher.explain(query, hit.doc), hit.doc);
        double explained = expl.getValue().doubleValue();
        boolean ok = Math.abs(explained - rescoredValue) < 1e-4;
        if (!ok) bad++;
        System.out.println((ok ? "ok    " : "WRONG ") + "doc=" + hit.doc + " rescored=" + rescoredValue + " explained=" + explained);
      }
    }
    if (bad > 0) throw new AssertionError(bad + " explanations disagree with the rescored value");
  }
}
