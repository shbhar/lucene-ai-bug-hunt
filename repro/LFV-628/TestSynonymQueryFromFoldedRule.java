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
package org.apache.lucene.analysis.synonym;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * The synonym rule "r&eacute;sum&eacute;, resume, cv", parsed with ASCII folding, maps "resume" to
 * itself, so QueryBuilder turns the query "resume" into SynonymQuery(cv, resume, resume). Its
 * pseudo-term statistics must still stay within the field's totals.
 */
public class TestSynonymQueryFromFoldedRule extends LuceneTestCase {

  /** Records the statistics a weight passes to the similarity, then delegates to BM25. */
  private static final class RecordingSimilarity extends Similarity {
    private final Similarity in = new BM25Similarity();
    final List<TermStats> seen = new ArrayList<>();
    FieldStats field;

    @Override
    public SimScorer scorer(float boost, FieldStats fieldStats, TermStats... termStats) {
      field = fieldStats;
      seen.addAll(List.of(termStats));
      return in.scorer(boost, fieldStats, termStats);
    }
  }

  /** whitespace, lower-casing, ASCII folding */
  private static final class FoldingAnalyzer extends Analyzer {
    private final SynonymMap synonyms;

    FoldingAnalyzer(SynonymMap synonyms) {
      this.synonyms = synonyms;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer tokenizer = new WhitespaceTokenizer();
      TokenStream stream = new ASCIIFoldingFilter(new LowerCaseFilter(tokenizer));
      if (synonyms != null) {
        stream = new SynonymGraphFilter(stream, synonyms, true);
      }
      return new TokenStreamComponents(tokenizer, stream);
    }
  }

  public void testFoldedRuleStaysWithinFieldStats() throws Exception {
    SolrSynonymParser parser = new SolrSynonymParser(true, true, new FoldingAnalyzer(null));
    parser.parse(new StringReader("r\u00e9sum\u00e9, resume, cv"));
    Query query =
        new QueryBuilder(new FoldingAnalyzer(parser.build())).createBooleanQuery("f", "resume");
    assertEquals("Synonym(f:cv f:resume f:resume)", query.toString());
    assertTrue(query instanceof SynonymQuery);

    try (Directory dir = newDirectory()) {
      // field "f" holds 3 tokens: "resume" twice, "cv" once
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new FoldingAnalyzer(null)))) {
        for (String text : new String[] {"resume", "resume cv"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        RecordingSimilarity sim = new RecordingSimilarity();
        searcher.setSimilarity(sim);
        searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f);
        assertEquals(1, sim.seen.size());
        TermStats stats = sim.seen.get(0);
        assertEquals(3, sim.field.sumTotalTermFreq());
        assertTrue(
            query
                + ": docFreq="
                + stats.docFreq()
                + " totalTermFreq="
                + stats.totalTermFreq()
                + " exceeds the field's sumTotalTermFreq="
                + sim.field.sumTotalTermFreq(),
            stats.totalTermFreq() <= sim.field.sumTotalTermFreq());
      }
    }
  }
}
