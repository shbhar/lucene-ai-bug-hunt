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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * For fields indexed without positions, MatchRegionRetriever's default strategy is
 * OffsetsFromTokens, which re-analyzes the value and keeps the tokens "that the query hinted at
 * when passed a QueryVisitor". It only listens to QueryVisitor#consumeTerms, so a multi-term query
 * (which reports its terms through consumeTermsMatching) matches the document but gets no hit
 * regions, while the equivalent TermQuery does.
 */
public class TestOffsetsFromTokensMultiTermQuery extends LuceneTestCase {

  private Map<String, List<OffsetRange>> hits(Query query) throws IOException {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    FieldType noPositions = new FieldType(TextField.TYPE_STORED);
    noPositions.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    noPositions.freeze();
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("tags", "foo bar", noPositions));
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query rewritten = searcher.rewrite(query);
        TopDocs topDocs = searcher.search(rewritten, 10);
        assertEquals("the query matches the document", 1, topDocs.scoreDocs.length);
        List<Map<String, List<OffsetRange>>> all = new ArrayList<>();
        new MatchRegionRetriever(searcher, rewritten, analyzer, _ -> false, _ -> true)
            .highlightDocuments(
                topDocs, (_, _, _, _, fieldHits) -> all.add(new TreeMap<>(fieldHits)));
        assertEquals(1, all.size());
        return all.get(0);
      }
    }
  }

  public void testTermQueryControl() throws IOException {
    assertEquals(
        Map.of("tags", List.of(new OffsetRange(4, 7))),
        hits(new TermQuery(new Term("tags", "bar"))));
  }

  public void testPrefixQuery() throws IOException {
    assertEquals(
        Map.of("tags", List.of(new OffsetRange(4, 7))),
        hits(new PrefixQuery(new Term("tags", "ba"))));
  }

  public void testWildcardQuery() throws IOException {
    assertEquals(
        Map.of("tags", List.of(new OffsetRange(4, 7))),
        hits(new WildcardQuery(new Term("tags", "b?r"))));
  }

  public void testFuzzyQueryControl() throws IOException {
    // passes: FuzzyQuery rewrites to term queries (scoring rewrite), which report consumeTerms
    assertEquals(
        Map.of("tags", List.of(new OffsetRange(4, 7))),
        hits(new FuzzyQuery(new Term("tags", "baz"), 1)));
  }

  public void testTermRangeQuery() throws IOException {
    assertEquals(
        Map.of("tags", List.of(new OffsetRange(4, 7))),
        hits(TermRangeQuery.newStringRange("tags", "b", "c", true, true)));
  }
}
