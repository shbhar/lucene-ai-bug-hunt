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

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Prints hits and rewritten queries for lone-group and neighbouring shapes; never fails. */
public class TestLoneGroupCompatProbe extends LuceneTestCase {
  private static final String F = "name";
  private static final String[] DOCS = {
    "john smith", "jonathan smith", "johnny walker", "peter jonathan", "john jonathan"
  };
  private static final String[] QUERIES = {
    "\"(john OR jonathan)\"",
    "\"(jo* -john)\"",
    "(john OR jonathan)",
    "(jo* -john)",
    "\"jo* -john\"",
    "\"john OR jonathan\"",
    "\"(john OR jonathan)\"~2",
    "\"((john OR jonathan))\"",
    "\"(john OR jonathan)^2\"",
    "\"+(jo* -john)\"",
    "\"(john)\"",
    "\"(jo*)\"",
    "\"(-john)\"",
    "\"(john OR jonathan) smith\"",
    "\"(jo* -john) smith\"",
    "\"(john OR jonathan) (smith OR walker)\"",
    "\"john jonathan\""
  };

  public void testProbe() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        for (String text : DOCS) {
          Document doc = new Document();
          doc.add(newTextField(F, text, Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        for (String s : QUERIES) {
          StringBuilder line = new StringBuilder("PROBE q=" + s);
          try {
            Query q = new ComplexPhraseQueryParser(F, analyzer).parse(s);
            Set<String> hits = new TreeSet<>();
            for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
              hits.add(searcher.storedFields().document(sd.doc).get(F));
            }
            line.append(" | rewritten=").append(searcher.rewrite(q));
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
