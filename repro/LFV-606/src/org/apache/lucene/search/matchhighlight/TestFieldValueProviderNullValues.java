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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MatchRegionRetriever.FieldValueProvider#getValues may return null "if the field is not loaded or
 * does not exist"; highlightDocument "can be used with custom document FieldValueProvider". The
 * default offset strategies dereference the returned list without a null check.
 */
public class TestFieldValueProviderNullValues extends LuceneTestCase {

  /** A provider that has not loaded any field: getValues returns null, as its javadoc allows. */
  private static final MatchRegionRetriever.FieldValueProvider NOTHING_LOADED =
      new MatchRegionRetriever.FieldValueProvider() {
        @Override
        public List<String> getValues(String field) {
          return null;
        }

        @Override
        public Iterator<String> iterator() {
          return Collections.emptyIterator();
        }
      };

  private Map<String, List<OffsetRange>> highlight(IndexOptions options) throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    FieldType type = new FieldType(TextField.TYPE_STORED);
    type.setIndexOptions(options);
    type.freeze();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("body", "foo bar", type));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new TermQuery(new Term("body", "foo"));
        MatchRegionRetriever retriever =
            new MatchRegionRetriever(searcher, query, analyzer, _ -> false, _ -> true);
        LeafReaderContext leaf = reader.leaves().get(0);
        Map<String, List<OffsetRange>> highlights = new HashMap<>();
        // No values for the field: nothing to highlight (as for a field that is not stored).
        retriever.highlightDocument(leaf, 0, NOTHING_LOADED, _ -> Integer.MAX_VALUE, highlights);
        return highlights;
      }
    }
  }

  public void testPositionsField() throws Exception {
    // OffsetsFromPositions: no values, so nothing to highlight (as for a field that is not stored)
    assertEquals(Map.of(), highlight(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS));
  }

  public void testDocsAndFreqsField() throws Exception {
    // OffsetsFromTokens: no values to re-analyze, so nothing to highlight
    assertEquals(Map.of(), highlight(IndexOptions.DOCS_AND_FREQS));
  }

  public void testOffsetsField() throws Exception {
    // Control: offsets come from the index and need no values; this one works.
    assertEquals(
        Map.of("body", List.of(new OffsetRange(0, 3))),
        highlight(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS));
  }
}
