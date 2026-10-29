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
package org.apache.lucene.search.suggest.document;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestContextQueryExactContextBoost extends LuceneTestCase {

  public void testExactContextBoostNotAppliedToLongerContext() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        Analyzer analyzer = new StandardAnalyzer()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      iwc.setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new ContextSuggestField("suggest", "lamp", 5, "redwood"));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        ContextQuery query =
            new ContextQuery(new PrefixCompletionQuery(analyzer, new Term("suggest", "la")));
        query.addContext("red", 10, true);
        query.addAllContexts();
        TopSuggestDocs top = new SuggestIndexSearcher(reader).suggest(query, 1, false);
        // "redwood" is not the exact context "red": expected the plain weight 5, actual 5 * 10
        assertEquals("score of lamp/redwood", 5f, top.scoreLookupDocs()[0].score, 0f);
      }
    }
  }
}
