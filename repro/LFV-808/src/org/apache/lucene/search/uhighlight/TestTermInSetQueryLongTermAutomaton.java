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

import java.util.List;
import java.util.function.Supplier;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.ByteRunnable;

/**
 * TermInSetQuery#visit reports a query with two or more terms through
 * QueryVisitor#consumeTermsMatching with a supplier that builds Automata.makeBinaryStringUnion over
 * all terms, and that builder rejects any term longer than Automata.MAX_STRING_UNION_TERM_LENGTH
 * (1000 bytes). TermInSetQuery itself accepts such terms (they can be indexed up to
 * IndexWriter.MAX_TERM_LENGTH = 32766 bytes) and searches with them, but the supplier throws, so
 * the UnifiedHighlighter without WEIGHT_MATCHES fails on the query.
 */
public class TestTermInSetQueryLongTermAutomaton extends LuceneTestCase {

  private static final String LONG_TERM = "x".repeat(1001);

  private static TermInSetQuery query() {
    return new TermInSetQuery("body", List.of(new BytesRef("short"), new BytesRef(LONG_TERM)));
  }

  /** The automaton handed to the visitor must accept the query's terms, and only them. */
  public void testVisitorAutomatonAcceptsEveryTerm() {
    ByteRunnable[] automaton = new ByteRunnable[1];
    query()
        .visit(
            new QueryVisitor() {
              @Override
              public void consumeTermsMatching(
                  Query query, String field, Supplier<ByteRunnable> supplier) {
                automaton[0] = supplier.get();
              }
            });
    assertNotNull(automaton[0]);
    assertTrue(accepts(automaton[0], new BytesRef("short")));
    assertTrue(accepts(automaton[0], new BytesRef(LONG_TERM)));
    assertFalse(accepts(automaton[0], new BytesRef("shor")));
  }

  /** A UnifiedHighlighter that does not use WEIGHT_MATCHES must highlight the query. */
  public void testHighlightWithoutWeightMatches() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    Document doc = new Document();
    FieldType withOffsets = new FieldType(TextField.TYPE_STORED); // highlight() wants offsets
    withOffsets.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    doc.add(new Field("body", "a short text", withOffsets));
    w.addDocument(doc);
    w.close();
    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);
    Query query = query();
    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(1, topDocs.scoreDocs.length); // the query itself is fine
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
    String[] snippets = highlighter.highlight("body", query, topDocs);
    assertArrayEquals(new String[] {"a <b>short</b> text"}, snippets);
    reader.close();
    dir.close();
  }

  private static boolean accepts(ByteRunnable a, BytesRef term) {
    int state = 0;
    for (int i = term.offset; i < term.offset + term.length && state != -1; i++) {
      state = a.step(state, term.bytes[i] & 0xff);
    }
    return state != -1 && a.isAccept(state);
  }
}
