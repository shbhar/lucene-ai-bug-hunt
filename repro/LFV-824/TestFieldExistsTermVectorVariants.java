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
package org.apache.lucene.search.highlight;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Variants: the FieldExistsQuery targets the highlighted field itself (which indexes norms), the
 * clause is MUST, the token stream comes from the non-deprecated TokenSources.getTokenStream
 * convenience (term vectors first, analysis as fallback), and QueryScorer has no field restriction.
 */
public class TestFieldExistsTermVectorVariants extends LuceneTestCase {

  private static final String TEXT = "the quick brown fox";

  private static String highlight(boolean positions) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      FieldType tv = new FieldType(TextField.TYPE_STORED);
      tv.setStoreTermVectors(true);
      tv.setStoreTermVectorPositions(positions);
      tv.setStoreTermVectorOffsets(true);
      tv.freeze();
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document d = new Document();
        d.add(new Field("body", TEXT, tv));
        w.addDocument(d);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("body", "fox")), Occur.MUST)
                .add(new FieldExistsQuery("body"), Occur.MUST)
                .build();
        TopDocs td = new IndexSearcher(reader).search(q, 10);
        assertEquals(1, td.scoreDocs.length);
        int doc = td.scoreDocs[0].doc;
        Fields tvFields = reader.termVectors().get(doc);
        String text = reader.storedFields().document(doc).get("body");
        TokenStream ts = TokenSources.getTokenStream("body", tvFields, text, analyzer, -1);
        Highlighter highlighter = new Highlighter(new QueryScorer(q));
        return highlighter.getBestFragment(ts, text);
      }
    }
  }

  /** Term vectors with positions and offsets: the TermVectorLeafReader path. */
  public void testTermVectorsWithPositionsAndOffsets() throws Exception {
    assertEquals("the quick brown <B>fox</B>", highlight(true));
  }

  /** Control: offsets without positions, so the extractor re-indexes into a MemoryIndex. */
  public void testTermVectorsWithOffsetsOnly() throws Exception {
    assertEquals("the quick brown <B>fox</B>", highlight(false));
  }
}
