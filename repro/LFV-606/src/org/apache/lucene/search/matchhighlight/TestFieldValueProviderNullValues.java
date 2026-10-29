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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFieldValueProviderNullValues extends LuceneTestCase {

  public void testNullValuesGiveNoHighlights() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("body", "foo bar", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        MatchRegionRetriever retriever =
            new MatchRegionRetriever(
                new IndexSearcher(reader),
                new TermQuery(new Term("body", "foo")),
                analyzer,
                _ -> false,
                _ -> true);
        MatchRegionRetriever.FieldValueProvider noValues =
            new MatchRegionRetriever.FieldValueProvider() {
              @Override
              public List<String> getValues(String field) {
                return null; // allowed: "null if the field is not loaded or does not exist"
              }

              @Override
              public Iterator<String> iterator() {
                return Collections.emptyIterator();
              }
            };
        Map<String, List<OffsetRange>> highlights = new HashMap<>();
        try {
          retriever.highlightDocument(
              reader.leaves().get(0), 0, noValues, _ -> Integer.MAX_VALUE, highlights);
        } catch (NullPointerException e) {
          throw new AssertionError("expected no highlights for null field values, got " + e, e);
        }
        assertEquals(Map.of(), highlights);
      }
    }
  }
}
