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
package org.apache.lucene.search.matchhighlight;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestOffsetsFromTokensPrefixQuery extends LuceneTestCase {

  public void testPrefixQueryOnFieldWithoutPositions() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    FieldType noPositions = new FieldType(TextField.TYPE_STORED);
    noPositions.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("f", "foo bar", noPositions));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = searcher.rewrite(new PrefixQuery(new Term("f", "ba")));
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.totalHits.value());
        Map<String, List<OffsetRange>> regions = new HashMap<>();
        new MatchRegionRetriever(searcher, query, analyzer, _ -> false, _ -> true)
            .highlightDocuments(topDocs, (_, _, _, _, hits) -> regions.putAll(hits));
        // expected: "bar" at [4, 7), as TermQuery(f:bar) gives; actual: {} (no hit region at all)
        assertEquals(Map.of("f", List.of(new OffsetRange(4, 7))), regions);
      }
    }
  }
}
