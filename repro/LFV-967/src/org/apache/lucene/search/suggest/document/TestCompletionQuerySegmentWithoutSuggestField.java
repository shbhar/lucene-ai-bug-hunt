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

import java.io.IOException;
import java.util.Collection;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCompletionQuerySegmentWithoutSuggestField extends LuceneTestCase {

  public void testSearchSkipsSegmentWithoutSuggestField() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    IndexWriterConfig iwc =
        new IndexWriterConfig(analyzer)
            .setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()))
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new SuggestField("suggest", "apple", 1));
      w.addDocument(doc);
      w.commit();
      w.addDocument(new Document());
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        CollectorManager<TopSuggestDocsCollector, TopSuggestDocs> manager =
            new CollectorManager<>() {
              @Override
              public TopSuggestDocsCollector newCollector() {
                return new TopSuggestDocsCollector(1, false);
              }

              @Override
              public TopSuggestDocs reduce(Collection<TopSuggestDocsCollector> collectors)
                  throws IOException {
                return collectors.iterator().next().get();
              }
            };
        CompletionQuery query = new PrefixCompletionQuery(analyzer, new Term("suggest", "app"));
        TopSuggestDocs hits;
        try {
          hits = new IndexSearcher(reader).search(query, manager);
        } catch (NullPointerException e) {
          throw new AssertionError(
              "expected the suggestion 'apple' (segment 2 has no suggest field), got " + e, e);
        }
        assertEquals(1, hits.scoreLookupDocs().length);
      }
    }
  }
}
