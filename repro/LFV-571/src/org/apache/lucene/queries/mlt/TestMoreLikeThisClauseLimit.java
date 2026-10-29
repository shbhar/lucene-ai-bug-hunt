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
package org.apache.lucene.queries.mlt;

import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMoreLikeThisClauseLimit extends LuceneTestCase {

  public void testLoweredClauseLimitKeepsBestTerms() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("text", "alpha beta gamma delta", Field.Store.NO));
      w.addDocument(doc);
    }
    DirectoryReader reader = DirectoryReader.open(dir);
    MoreLikeThis mlt = new MoreLikeThis(reader);
    mlt.setAnalyzer(analyzer);
    mlt.setMinTermFreq(1);
    mlt.setMinDocFreq(1);
    String like = "alpha alpha alpha alpha beta beta beta gamma gamma delta";
    Set<Term> terms = new HashSet<>();
    int saved = IndexSearcher.getMaxClauseCount();
    IndexSearcher.setMaxClauseCount(2);
    try {
      mlt.like("text", new StringReader(like)).visit(QueryVisitor.termCollector(terms));
    } finally {
      IndexSearcher.setMaxClauseCount(saved);
    }
    // expected the 2 best terms [alpha, beta] (tf 4, 3); actual the 2 worst [delta, gamma] (tf 1, 2)
    assertEquals(List.of("alpha", "beta"), terms.stream().map(Term::text).sorted().toList());
    reader.close();
    dir.close();
    analyzer.close();
  }
}
