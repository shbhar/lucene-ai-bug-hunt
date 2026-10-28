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
package org.apache.lucene.monitor;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Monitor matches a single document through a MemoryIndex and a batch of documents through a real
 * in-memory IndexWriter index (DocumentBatch: "MemoryIndex benchmarks as better performing than
 * RAMDirectory for this case"). The same stored phrase query should therefore match the same
 * document in both modes.
 */
public class TestMonitorSingleVsBatchPositions extends LuceneTestCase {

  public void testPhraseAcrossValuesSingleVsBatch() throws IOException {
    CharArraySet stop = new CharArraySet(List.of("the"), false);
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new StandardTokenizer();
            TokenStream ts = new StopFilter(new LowerCaseFilter(tokenizer), stop);
            return new TokenStreamComponents(tokenizer, ts);
          }
        };
    Document doc = new Document();
    doc.add(new TextField("body", "quick the", Field.Store.NO));
    doc.add(new TextField("body", "fox", Field.Store.NO));
    Document other = new Document();
    other.add(new TextField("body", "unrelated", Field.Store.NO));

    try (Monitor monitor = new Monitor(analyzer)) {
      monitor.register(new MonitorQuery("q", new PhraseQuery("body", "quick", "fox")));
      int single = monitor.match(doc, QueryMatch.SIMPLE_MATCHER).getMatchCount();
      int inBatch =
          monitor.match(new Document[] {doc, other}, QueryMatch.SIMPLE_MATCHER).getMatchCount(0);
      assertEquals(
          "batch (IndexWriter) matches=" + inBatch + ", single (MemoryIndex) matches=" + single,
          inBatch,
          single);
    }
  }
}
