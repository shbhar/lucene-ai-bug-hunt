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
package org.apache.lucene.search.matchhighlight.v1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.matchhighlight.FieldValueHighlighters;
import org.apache.lucene.search.matchhighlight.MatchHighlighter;
import org.apache.lucene.search.matchhighlight.MatchRegionRetriever;
import org.apache.lucene.search.matchhighlight.OffsetRange;
import org.apache.lucene.search.matchhighlight.PassageFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, different package): MatchRegionRetriever with its default
 * offset strategies on a field indexed without positions (DOCS / DOCS_AND_FREQS) should return the
 * hit region of the matching token for a multi-term query, as it does for a TermQuery on the same
 * token and as it does for the same multi-term query on a positional field.
 *
 * <p>Document value: "apple banana cherry"; the only matching token is "banana" at [6, 12).
 */
public class TestNoPositionsMultiTermHighlight extends LuceneTestCase {

  private static final String VALUE = "apple banana cherry";
  private static final String EXPECTED = "[6,12)";

  private static Analyzer whitespace() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new WhitespaceTokenizer());
      }
    };
  }

  private static FieldType storedTextType(IndexOptions options) {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(options);
    ft.freeze();
    return ft;
  }

  /** Index one document whose field {@code fld} has {@link #VALUE}, run {@code q}, return hits. */
  private String regions(IndexOptions options, Query q) throws IOException {
    Analyzer analyzer = whitespace();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("fld", VALUE, storedTextType(options)));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query rewritten = searcher.rewrite(q);

        // Precondition 1: the document is a hit.
        TopDocs topDocs = searcher.search(rewritten, 10);
        assertEquals("document must match " + q, 1, topDocs.totalHits.value());

        // Precondition 2: the Matches API reports a non-empty match on the field.
        Weight weight = searcher.createWeight(rewritten, ScoreMode.COMPLETE, 1f);
        LeafReaderContext leaf = reader.leaves().get(0);
        Matches matches = weight.matches(leaf, topDocs.scoreDocs[0].doc - leaf.docBase);
        assertNotNull("Matches must be non-null for " + q, matches);
        MatchesIterator mi = matches.getMatches("fld");
        assertNotNull("MatchesIterator for fld must be non-null for " + q, mi);
        assertTrue("MatchesIterator must have at least one match for " + q, mi.next());

        // Highlight via the default-strategy constructor.
        Map<String, List<OffsetRange>> all = new TreeMap<>();
        MatchRegionRetriever retriever =
            new MatchRegionRetriever(searcher, rewritten, analyzer, f -> false, f -> true);
        retriever.highlightDocuments(
            topDocs, (docId, leafReader, leafDocId, fieldValues, hits) -> all.putAll(hits));
        List<OffsetRange> r = all.get("fld");
        if (r == null) {
          return "[]";
        }
        return r.stream().map(o -> "[" + o.from + "," + o.to + ")").collect(Collectors.joining());
      }
    }
  }

  // ---- controls that must pass ----

  public void testControlTermQueryDocsOnly() throws IOException {
    assertEquals(EXPECTED, regions(IndexOptions.DOCS, new TermQuery(new Term("fld", "banana"))));
  }

  public void testControlPrefixQueryOnPositionalField() throws IOException {
    assertEquals(
        EXPECTED,
        regions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS, new PrefixQuery(new Term("fld", "ban"))));
  }

  public void testControlPrefixQueryScoringRewriteDocsOnly() throws IOException {
    // Same query and field as testPrefixQueryDocsOnly; only the rewrite method differs.
    assertEquals(
        EXPECTED,
        regions(
            IndexOptions.DOCS,
            new PrefixQuery(new Term("fld", "ban"), MultiTermQuery.SCORING_BOOLEAN_REWRITE)));
  }

  // ---- claim: default (constant-score) rewrite on a no-positions field ----

  public void testPrefixQueryDocsOnly() throws IOException {
    assertEquals(EXPECTED, regions(IndexOptions.DOCS, new PrefixQuery(new Term("fld", "ban"))));
  }

  public void testPrefixQueryDocsAndFreqs() throws IOException {
    assertEquals(
        EXPECTED, regions(IndexOptions.DOCS_AND_FREQS, new PrefixQuery(new Term("fld", "ban"))));
  }

  public void testWildcardQueryDocsOnly() throws IOException {
    assertEquals(EXPECTED, regions(IndexOptions.DOCS, new WildcardQuery(new Term("fld", "b*a"))));
  }

  public void testRegexpQueryDocsOnly() throws IOException {
    assertEquals(EXPECTED, regions(IndexOptions.DOCS, new RegexpQuery(new Term("fld", "ba.*"))));
  }

  public void testTermRangeQueryDocsOnly() throws IOException {
    assertEquals(
        EXPECTED, regions(IndexOptions.DOCS, TermRangeQuery.newStringRange("fld", "b", "c", true, false)));
  }

  public void testTermInSetQueryTwoTermsDocsOnly() throws IOException {
    assertEquals(
        EXPECTED,
        regions(
            IndexOptions.DOCS,
            new TermInSetQuery("fld", List.of(new BytesRef("banana"), new BytesRef("zzz")))));
  }

  // ---- end to end through the ready-to-use MatchHighlighter ----

  public void testMatchHighlighterPrefixQueryDocsOnly() throws IOException {
    Analyzer analyzer = whitespace();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("fld", VALUE, storedTextType(IndexOptions.DOCS)));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        List<String> out = new ArrayList<>();
        for (Query q :
            new Query[] {
              new TermQuery(new Term("fld", "banana")), new PrefixQuery(new Term("fld", "ban"))
            }) {
          TopDocs topDocs = searcher.search(q, 10);
          assertEquals(1, topDocs.totalHits.value());
          MatchHighlighter hl =
              new MatchHighlighter(searcher, analyzer)
                  .appendFieldHighlighter(
                      FieldValueHighlighters.highlighted(
                          80, 1, new PassageFormatter("...", ">", "<"), "fld"::equals))
                  .appendFieldHighlighter(FieldValueHighlighters.skipRemaining());
          List<String> snippets =
              hl.highlight(topDocs, q)
                  .map(h -> String.valueOf(h.fields.get("fld")))
                  .collect(Collectors.toList());
          out.add(q.getClass().getSimpleName() + "=" + snippets);
        }
        assertEquals("[TermQuery=[[apple >banana< cherry]], PrefixQuery=[[apple >banana< cherry]]]",
            out.toString());
      }
    }
  }
}
