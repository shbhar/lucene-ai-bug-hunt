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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * A sorted search with MatchAllDocsQuery on a plain IndexSearcher collects every document: the
 * comparators' competitive iterators (points, doc-values skip index, terms) never skip anything.
 * The same search with a TermQuery that matches the same documents skips most of them, as does the
 * same MatchAllDocsQuery search on 10.2.
 *
 * <p>The assertion is the one TestSortOptimization makes ("Expected some non-competitive hits are
 * skipped", assertNonCompetitiveHitsAreSkipped), but on a default IndexSearcher instead of one
 * that hides the bulk scorers.
 */
public class TestSortPruningMatchAll extends LuceneTestCase {

  private static final int NUM_DOCS = 100_000;

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        doc.add(new StringField("all", "x", Field.Store.NO));
        doc.add(new LongPoint("points", i));
        doc.add(new NumericDocValuesField("points", i));
        doc.add(NumericDocValuesField.indexedField("skipper", i));
        BytesRef term = new BytesRef(String.format(java.util.Locale.ROOT, "%08d", i));
        doc.add(new StringField("terms", term, Field.Store.NO));
        doc.add(new SortedDocValuesField("terms", term));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private long collected(Query query, SortField sortField) throws IOException {
    TopFieldDocs td = searcher.search(query, 10, new Sort(sortField));
    return td.totalHits.value();
  }

  private void check(SortField sortField) throws IOException {
    long viaTermQuery = collected(new TermQuery(new Term("all", "x")), sortField);
    assertTrue(
        "control: TermQuery should skip non-competitive hits, collected " + viaTermQuery,
        viaTermQuery < NUM_DOCS);
    long viaMatchAll = collected(new MatchAllDocsQuery(), sortField);
    assertTrue(
        "Expected some non-competitive hits are skipped; got collected_hits="
            + viaMatchAll
            + " num_docs="
            + NUM_DOCS
            + " (TermQuery over the same docs: "
            + viaTermQuery
            + ")",
        viaMatchAll < NUM_DOCS);
  }

  public void testLongSortWithPoints() throws IOException {
    check(new SortField("points", SortField.Type.LONG));
  }

  public void testLongSortWithSkipIndex() throws IOException {
    check(new SortField("skipper", SortField.Type.LONG));
  }

  public void testStringSortWithTermsIndex() throws IOException {
    TopFieldDocs td =
        searcher.search(
            new TermQuery(new Term("all", "x")), 10, new Sort(new SortField("terms", SortField.Type.STRING)));
    long viaTermQuery = td.totalHits.value();
    assertTrue("control: " + viaTermQuery, viaTermQuery < NUM_DOCS);
    td =
        searcher.search(
            new MatchAllDocsQuery(), 10, new Sort(new SortField("terms", SortField.Type.STRING)));
    assertTrue(
        "Expected some non-competitive hits are skipped; got collected_hits="
            + td.totalHits.value()
            + " (TermQuery: "
            + viaTermQuery
            + ")",
        td.totalHits.value() < NUM_DOCS);
  }
}
