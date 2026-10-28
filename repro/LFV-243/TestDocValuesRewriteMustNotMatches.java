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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A MUST_NOT clause rewritten with MultiTermQuery.DOC_VALUES_REWRITE on a doc-values-only field
 * (SortedSetDocValuesField#newSlowSetQuery) excludes documents from the hits, but its
 * Weight#matches returns null for the excluded documents too. BooleanWeight#matches treats a null
 * prohibited clause as "not excluded", so the BooleanQuery's Weight#matches returns non-null for a
 * document that the same query does not return.
 *
 * <p>Weight#matches javadoc: "Returns Matches for a specific document, or null if the document does
 * not match the parent query."
 */
public class TestDocValuesRewriteMustNotMatches extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      // doc 0 is "public", doc 1 is "restricted"
      for (String label : new String[] {"public", "restricted"}) {
        Document doc = new Document();
        doc.add(new StringField("id", label, Field.Store.YES));
        doc.add(new StringField("body", "fruit", Field.Store.NO));
        // doc-values only: no postings for this field
        doc.add(new SortedSetDocValuesField("label_dv", new BytesRef(label)));
        // postings and doc values (control)
        doc.add(new KeywordField("label_kw", label, Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private int docOf(String id) throws IOException {
    TopDocs td = searcher.search(new TermQuery(new Term("id", id)), 1);
    assertEquals(1, td.totalHits.value());
    return td.scoreDocs[0].doc;
  }

  private Matches matches(Query query, int globalDoc) throws IOException {
    Weight w = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
    List<LeafReaderContext> leaves = reader.leaves();
    LeafReaderContext ctx = leaves.get(ReaderUtil.subIndex(globalDoc, leaves));
    return w.matches(ctx, globalDoc - ctx.docBase);
  }

  private static Query bodyFruitExcluding(Query deny) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", "fruit")), BooleanClause.Occur.MUST)
        .add(deny, BooleanClause.Occur.MUST_NOT)
        .build();
  }

  private void assertDenyClauseRespected(Query deny) throws IOException {
    Query q = bodyFruitExcluding(deny);
    int restricted = docOf("restricted");
    int pub = docOf("public");
    TopDocs td = searcher.search(q, 10);
    // hits are correct in both variants: only the public doc is returned
    assertEquals("hits of " + q, 1, td.totalHits.value());
    assertEquals(pub, td.scoreDocs[0].doc);
    assertNotNull("public doc is a hit, Matches must be non-null: " + q, matches(q, pub));
    // the restricted doc is NOT a hit, so Weight#matches must be null for it
    assertNull(
        "restricted doc is not a hit of " + q + " but Weight#matches returned non-null",
        matches(q, restricted));
  }

  public void testControlKeywordFieldDenyClause() throws IOException {
    // field with postings and doc values: matches() is null for the excluded doc
    assertDenyClauseRespected(KeywordField.newSetQuery("label_kw", List.of(new BytesRef("restricted"))));
  }

  public void testControlTermQueryDenyClause() throws IOException {
    assertDenyClauseRespected(new TermQuery(new Term("label_kw", "restricted")));
  }

  public void testDocValuesOnlyNewSlowSetQueryDenyClause() throws IOException {
    assertDenyClauseRespected(
        SortedSetDocValuesField.newSlowSetQuery("label_dv", List.of(new BytesRef("restricted"))));
  }

  public void testDocValuesOnlyPrefixDenyClause() throws IOException {
    assertDenyClauseRespected(
        new PrefixQuery(new Term("label_dv", "restr"), MultiTermQuery.DOC_VALUES_REWRITE));
  }
}
