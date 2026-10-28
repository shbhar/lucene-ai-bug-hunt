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
package org.apache.lucene.queryparser.surround.query;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Surround: a boost written on the query that follows a field prefix, as in {@code title: lucene^3}, is
 * silently dropped. The parser stores the weight on the term (its toString() still shows {@code ^3.0}),
 * but FieldsQuery builds its operand with makeLuceneQueryFieldNoBoost, so no BoostQuery is created.
 * Only scores and ranking are affected; the hit sets are correct.
 */
public class TestSrndFieldPrefixBoost extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    MockAnalyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      // doc "t": the word is in a long title; doc "b": the word is in a short body
      Document t = new Document();
      t.add(new StringField("id", "t", Field.Store.YES));
      t.add(new TextField("title", "lucene in action second edition", Field.Store.NO));
      w.addDocument(t);
      Document b = new Document();
      b.add(new StringField("id", "b", Field.Store.YES));
      b.add(new TextField("body", "lucene", Field.Store.NO));
      w.addDocument(b);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader); // default similarity, no randomisation
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private float score(String surround, String id) throws Exception {
    Query q = QueryParser.parse(surround).makeLuceneQueryField("body", new BasicQueryFactory());
    TopDocs td = searcher.search(q, 10);
    for (ScoreDoc sd : td.scoreDocs) {
      if (searcher.storedFields().document(sd.doc).get("id").equals(id)) return sd.score;
    }
    throw new AssertionError(id + " not found by " + surround);
  }

  private String top(String surround) throws Exception {
    Query q = QueryParser.parse(surround).makeLuceneQueryField("body", new BasicQueryFactory());
    TopDocs td = searcher.search(q, 10);
    return searcher.storedFields().document(td.scoreDocs[0].doc).get("id");
  }

  public void testBoostAfterFieldPrefixIsApplied() throws Exception {
    // the parser keeps the weight on the term
    assertEquals("or((title:lucene^3.0), (body:lucene))", QueryParser.parse("or(title: lucene^3, body: lucene)").toString());

    float unboosted = score("or(title: lucene, body: lucene)", "t");
    // control: the same boost written on the parenthesised field query is applied
    float control = score("or((title: lucene)^3, body: lucene)", "t");
    assertEquals(3f * unboosted, control, 1e-5f * control);
    assertEquals("t", top("or((title: lucene)^3, body: lucene)"));

    // documented boosting (QueryParser javadoc: "boosting (via ^)"); the field prefix must not remove it
    assertEquals("or(title: lucene^3, body: lucene)", control, score("or(title: lucene^3, body: lucene)", "t"), 1e-5f * control);
    assertEquals("(title: lucene^3) or (body: lucene)", control, score("(title: lucene^3) or (body: lucene)", "t"), 1e-5f * control);
    assertEquals("ranking of or(title: lucene^3, body: lucene)", "t", top("or(title: lucene^3, body: lucene)"));
  }

  public void testBoostAfterMultiFieldPrefixIsApplied() throws Exception {
    // "title: body: q" is documented as an OR of q over both fields (FieldsQuery javadoc); a boost on q
    // must reach both per-field clauses
    float control = score("or((title: body: lucene)^3, body: lucene)", "t");
    assertEquals("or(title: body: lucene^3, body: lucene)", control, score("or(title: body: lucene^3, body: lucene)", "t"), 1e-5f * control);
  }
}
