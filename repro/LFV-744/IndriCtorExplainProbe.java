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
package demo;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.IndriDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/** Prints explain() and a top-10 search for the two constructors with the same mu (print only). */
public class IndriCtorExplainProbe {
  public static void main(String[] args) throws Exception {
    String[] docs = {
      "president washington was the first leader",
      "the president is head of the executive branch",
      "george washington was a general",
      "a company can have a president"
    };
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        for (String text : docs) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q = new TermQuery(new Term("body", "president"));
        Similarity[] sims = {new IndriDirichletSimilarity(), new IndriDirichletSimilarity(2000f)};
        String[] labels = {"new IndriDirichletSimilarity()", "new IndriDirichletSimilarity(2000f)"};
        for (int i = 0; i < sims.length; i++) {
          IndexSearcher searcher = new IndexSearcher(reader);
          searcher.setSimilarity(sims[i]);
          System.out.println("== " + labels[i] + "  toString=" + sims[i]);
          System.out.print(searcher.explain(q, 0));
          TopDocs td = searcher.search(q, 10);
          StringBuilder sb = new StringBuilder();
          for (ScoreDoc sd : td.scoreDocs) {
            sb.append(" doc").append(sd.doc).append('=').append(sd.score);
          }
          System.out.println("search(" + q + ", 10): " + td.scoreDocs.length + " hits" + sb);
        }
      }
    }
  }
}
