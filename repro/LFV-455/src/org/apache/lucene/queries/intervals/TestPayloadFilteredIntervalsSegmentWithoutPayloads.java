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
package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.IdentityEncoder;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Whether a segment "has payloads" for a field depends on the documents in it (FieldInfo#hasPayloads:
 * 'Returns true if any payloads exist for this field'). Intervals.term(term, payloadFilter) throws
 * IllegalArgumentException on any segment in which no document of the field carried a payload, so
 * a search over an index with such a segment fails as a whole, while the same documents in one
 * merged segment are searched fine.
 */
public class TestPayloadFilteredIntervalsSegmentWithoutPayloads extends LuceneTestCase {

  private static Analyzer payloadAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(
            t, new DelimitedPayloadTokenFilter(t, '|', new IdentityEncoder()));
      }
    };
  }

  public void testSegmentWithoutPayloads() throws IOException {
    try (Directory dir = newDirectory();
        Analyzer analyzer = payloadAnalyzer()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        String[] docs = {"big|adj dog|noun", "dog walks", "hot|adj dog|noun"};
        for (String text : docs) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
          w.commit(); // one segment per document; the second has no payloads at all
        }
      }
      IntervalsSource dogAsNoun =
          Intervals.term("dog", p -> p != null && p.utf8ToString().equals("noun"));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(3, reader.leaves().size());
        assertFalse(reader.leaves().get(1).reader().terms("f").hasPayloads());
        IndexSearcher searcher = newSearcher(reader);
        // the documents that contain "dog" with payload "noun": the first and the third
        assertEquals(2, searcher.count(new IntervalQuery("f", dogAsNoun)));
      }
    }
  }

  /** Control: the same three documents in one segment. */
  public void testSingleSegmentControl() throws IOException {
    try (Directory dir = newDirectory();
        Analyzer analyzer = payloadAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String text : new String[] {"big|adj dog|noun", "dog walks", "hot|adj dog|noun"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      IntervalsSource dogAsNoun =
          Intervals.term("dog", p -> p != null && p.utf8ToString().equals("noun"));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        assertEquals(2, newSearcher(reader).count(new IntervalQuery("f", dogAsNoun)));
      }
    }
  }
}
