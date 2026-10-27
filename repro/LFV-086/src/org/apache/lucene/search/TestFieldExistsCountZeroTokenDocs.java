/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * IndexSearcher#count(FieldExistsQuery) must equal the number of documents the query matches. A
 * tokenized field without norms that also has doc values is used: a document whose text produces
 * no tokens still has the field (and its doc value), so FieldExistsQuery matches it, but it adds no
 * term, so Terms#getDocCount does not count it.
 */
public class TestFieldExistsCountZeroTokenDocs extends LuceneTestCase {

  private void check(boolean omitNorms, int expectedMatches) throws Exception {
    check(omitNorms, expectedMatches, new FieldExistsQuery("title"));
  }

  private void check(boolean omitNorms, int expectedMatches, Query q) throws Exception {
    FieldType type = new FieldType(TextField.TYPE_NOT_STORED);
    type.setOmitNorms(omitNorms);
    type.freeze();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document d = new Document();
      d.add(new Field("title", "", type)); // the field is present, but produces no tokens
      d.add(new SortedDocValuesField("title", new BytesRef("")));
      w.addDocument(d);
      d = new Document();
      d.add(new Field("title", "hello", type));
      d.add(new SortedDocValuesField("title", new BytesRef("hello")));
      w.addDocument(d);
      d = new Document(); // no title at all
      d.add(new StringField("id", "3", Field.Store.NO));
      w.addDocument(d);
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        int hits = searcher.search(q, 10).scoreDocs.length;
        assertEquals(expectedMatches, hits);
        assertEquals("count(" + q + ") vs. collected hits", hits, searcher.count(q));
      }
    }
  }

  /** Bug: omitNorms=true, so the count comes from Terms#getDocCount and misses the empty doc. */
  public void testCountWithoutNorms() throws Exception {
    check(true, 2);
  }

  /** Bug, same root cause: no document has a token, Terms is null, and count() returns 0. */
  public void testCountWhenNoDocumentHasATerm() throws Exception {
    FieldType type = new FieldType(TextField.TYPE_NOT_STORED);
    type.setOmitNorms(true);
    type.freeze();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document d = new Document();
      d.add(new Field("title", " ", type));
      d.add(new SortedDocValuesField("title", new BytesRef(" ")));
      w.addDocument(d);
      d = new Document();
      d.add(new StringField("id", "2", Field.Store.NO));
      w.addDocument(d);
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q = new FieldExistsQuery("title");
        int hits = searcher.search(q, 10).scoreDocs.length;
        assertEquals(1, hits);
        assertEquals("count(" + q + ") vs. collected hits", hits, searcher.count(q));
      }
    }
  }

  /** Bug, same root cause: an unbounded doc-values range query rewrites to FieldExistsQuery. */
  public void testCountOfUnboundedDocValuesRange() throws Exception {
    check(true, 2, SortedDocValuesField.newSlowRangeQuery("title", null, null, true, true));
  }

  /** Control: with norms the count is taken from the norms branch and is correct. */
  public void testControlWithNorms() throws Exception {
    check(false, 2);
  }
}
