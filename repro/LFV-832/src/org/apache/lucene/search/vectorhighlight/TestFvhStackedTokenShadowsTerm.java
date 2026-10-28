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
package org.apache.lucene.search.vectorhighlight;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockSynonymAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FastVectorHighlighter with index-time synonyms: at a position that holds several tokens (here
 * "dogs" and its stacked synonym "dog"), FieldPhraseList only tries the first token that has any
 * entry in the query's phrase map. If that token only starts a phrase that does not continue, the
 * other token at the same position is never tried, so a plain TermQuery match there is dropped.
 */
public class TestFvhStackedTokenShadowsTerm extends LuceneTestCase {

  public void testTermMatchAtSynonymPositionIsHighlighted() throws Exception {
    Directory dir = newDirectory();
    // MockSynonymAnalyzer indexes "dogs" as "dogs" plus the stacked synonym "dog" (posInc 0)
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockSynonymAnalyzer()));
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    ft.setStoreTermVectorOffsets(true);
    ft.freeze();
    Document doc = new Document();
    doc.add(new Field("f", "my dogs", ft));
    w.addDocument(doc);
    w.close();
    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);

    // dogs OR "dog house": the document matches through the TermQuery on "dogs"
    Query termQuery = new TermQuery(new Term("f", "dogs"));
    Query phrase = new PhraseQuery("f", "dog", "house");
    Query query =
        new BooleanQuery.Builder()
            .add(termQuery, Occur.SHOULD)
            .add(phrase, Occur.SHOULD)
            .build();
    assertEquals(1, searcher.count(query));
    assertEquals(0, searcher.count(phrase));

    // Reference: Weight#matches reports the occurrence of "dogs" at offsets [3, 7)
    Weight weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1);
    Matches matches = weight.matches(reader.leaves().get(0), 0);
    assertNotNull(matches);
    MatchesIterator it = matches.getMatches("f");
    assertTrue(it.next());
    assertEquals(3, it.startOffset());
    assertEquals(7, it.endOffset());

    FastVectorHighlighter fvh = new FastVectorHighlighter(); // phraseHighlight=true, fieldMatch=true
    // control: the TermQuery on its own is highlighted
    assertEquals(
        "my <b>dogs</b>",
        fvh.getBestFragment(fvh.getFieldQuery(termQuery, reader), reader, 0, "f", 100));
    // the same TermQuery in a disjunction with a phrase starting with the stacked synonym
    String fragment = fvh.getBestFragment(fvh.getFieldQuery(query, reader), reader, 0, "f", 100);
    assertEquals("my <b>dogs</b>", fragment);

    reader.close();
    dir.close();
  }
}
