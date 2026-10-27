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
package org.apache.lucene.search.uhighlight;

import java.util.EnumSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter.HighlightFlag;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * UnifiedHighlighter.Builder#withFlags is documented to set the flags that "will override the flags
 * set by withHandleMultiTermQuery, withHighlightPhrasesStrictly, withPassageRelevancyOverSpeed and
 * withWeightMatches". The UnifiedHighlighter(Builder) constructor never reads it.
 */
public class TestUnifiedHighlighterBuilderWithFlags extends LuceneTestCase {

  public void testWithFlagsIsHonoured() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    FieldType type = new FieldType(TextField.TYPE_STORED);
    type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    type.freeze();
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("body", "alpha beta. alpha gamma.", type));
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        PhraseQuery query = new PhraseQuery("body", "alpha", "beta");
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.scoreDocs.length);

        // Only MULTI_TERM_QUERY: phrases are not highlighted strictly, so every occurrence of
        // every query term is highlighted.
        Set<HighlightFlag> requested = EnumSet.of(HighlightFlag.MULTI_TERM_QUERY);

        // Control: the same flag set requested through the boolean builder methods.
        UnifiedHighlighter viaBooleans =
            UnifiedHighlighter.builder(searcher, analyzer)
                .withBreakIterator(WholeBreakIterator::new)
                .withHighlightPhrasesStrictly(false)
                .withPassageRelevancyOverSpeed(false)
                .withWeightMatches(false)
                .build();
        assertEquals(requested, viaBooleans.getFlags("body"));
        String expected = viaBooleans.highlight("body", query, topDocs)[0];
        assertEquals("<b>alpha</b> <b>beta</b>. <b>alpha</b> gamma.", expected);

        UnifiedHighlighter viaWithFlags =
            UnifiedHighlighter.builder(searcher, analyzer)
                .withBreakIterator(WholeBreakIterator::new)
                .withFlags(requested)
                .build();
        assertEquals("flags given to Builder#withFlags", requested, viaWithFlags.getFlags("body"));
        assertEquals(expected, viaWithFlags.highlight("body", query, topDocs)[0]);
      }
    }
  }
}
