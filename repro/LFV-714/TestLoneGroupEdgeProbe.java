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
package org.apache.lucene.queryparser.complexPhrase;

import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Prints the rewritten query, its class and the scored hits (default BM25, plain IndexSearcher)
 * for shapes whose structure the lone-group fix may change; never fails.
 */
public class TestLoneGroupEdgeProbe extends LuceneTestCase {
  private static final String F = "name";
  private static final String[] DOCS = {
    "john smith", "jonathan smith", "johnny walker", "peter jonathan", "john jonathan"
  };
  private static final String[] QUERIES = {
    "\"(john OR jonathan)\"",
    "\"(john OR jonathan)^2\"",
    "\"(john OR jonathan) ^2\"",
    "\"(john)\"",
    "\"(jo*)\"",
    "\"(-john)\"",
    "\"(john OR jonathan) smith\"",
    "\"john\""
  };

  public void testProbe() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String text : DOCS) {
          Document doc = new Document();
          doc.add(newTextField(F, text, Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        for (String s : QUERIES) {
          StringBuilder line = new StringBuilder("EDGE q=" + s);
          try {
            Query q = new ComplexPhraseQueryParser(F, analyzer).parse(s);
            Query rewritten = searcher.rewrite(q);
            Map<String, Float> hits = new TreeMap<>();
            for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
              hits.put(searcher.storedFields().document(sd.doc).get(F), sd.score);
            }
            line.append(" | rewritten=").append(rewritten);
            line.append(" | class=").append(rewritten.getClass().getSimpleName());
            line.append(" | hits=").append(hits);
          } catch (Exception e) {
            line.append(" | EXCEPTION ").append(e.getClass().getSimpleName());
            line.append(": ").append(e.getMessage());
          }
          System.out.println(line);
        }
      }
    }
  }
}
