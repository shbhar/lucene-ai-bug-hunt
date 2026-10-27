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
package org.apache.lucene.search.suggest.document;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A SuggestField / ContextSuggestField mix under one field name may be rejected with
 * IllegalArgumentException (upstream's TestContextSuggestField#testMixedSuggestFields expects one),
 * but the rejection must not close the IndexWriter, drop other accepted documents, or leave
 * segments that can never be merged. Each test lets the operation that introduces the mix throw.
 */
public class TestCompletionMixedTypesClosesWriter extends LuceneTestCase {

  private static final PostingsFormat COMPLETION = new Completion104PostingsFormat();

  private static IndexWriterConfig config() {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setCodec(
        new Lucene104Codec() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            return "suggest".equals(field) ? COMPLETION : super.getPostingsFormatForField(field);
          }
        });
    iwc.setMergePolicy(new LogDocMergePolicy());
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static Document plain(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  private static Document suggest(String id, String value) {
    Document doc = plain(id);
    doc.add(new SuggestField("suggest", value, 1));
    return doc;
  }

  private static Document contextSuggest(String id, String value) {
    Document doc = plain(id);
    doc.add(new ContextSuggestField("suggest", value, 1, "ctx"));
    return doc;
  }

  private static void assertWriterOpen(IndexWriter w, String step) {
    Throwable tragic = w.getTragicException();
    assertNull(step + " closed the IndexWriter with tragic exception: " + tragic, tragic);
    assertTrue(step + ": IndexWriter is closed", w.isOpen());
  }

  private static void assertHasIds(Directory dir, String... ids) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      for (String id : ids) {
        assertEquals(
            "document " + id + " lost; committed ids must include " + Arrays.toString(ids),
            1,
            s.count(new TermQuery(new Term("id", id))));
      }
    }
  }

  /**
   * Two accepted documents, one SuggestField and one ContextSuggestField, in one in-memory segment:
   * the conflict surfaces only at flush and takes every other buffered document with it.
   */
  public void testTwoDocumentsInOneSegment() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(plain("committed"));
      w.commit();
      w.addDocument(plain("1"));
      w.addDocument(suggest("2", "apple"));
      try {
        w.addDocument(contextSuggest("3", "banana"));
        w.commit();
      } catch (IllegalArgumentException e) {
        // an up-front rejection of the conflicting document would be fine
      }
      assertWriterOpen(w, "commit");
      w.commit();
      w.close();
      assertHasIds(dir, "committed", "1", "2");
    }
  }

  /**
   * Upstream's single-document case (testMixedSuggestFields expects IllegalArgumentException from
   * addDocument or commit): the exception comes from the flush, which closes the writer and drops
   * the unrelated buffered document.
   */
  public void testOneDocumentWithBothTypesDropsOtherBufferedDocuments() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(plain("committed"));
      w.commit();
      w.addDocument(plain("1"));
      Document both = plain("2");
      both.add(new SuggestField("suggest", "apple", 4));
      both.add(new ContextSuggestField("suggest", "banana", 3));
      try {
        w.addDocument(both);
        w.commit();
      } catch (IllegalArgumentException e) {
        // expected by upstream's testMixedSuggestFields
      }
      assertWriterOpen(w, "commit of the mixed document");
      w.commit();
      w.close();
      assertHasIds(dir, "committed", "1");
    }
  }

  /**
   * Two segments that each flushed cleanly, one SuggestField and one ContextSuggestField: every
   * merge of the pair closes the writer, and a new writer on the same index fails the same way.
   */
  public void testTwoCleanSegmentsPoisonEveryMerge() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(suggest("1", "apple"));
      w.commit();
      try {
        w.addDocument(contextSuggest("2", "banana"));
        w.commit();
      } catch (IllegalArgumentException e) {
        // an up-front rejection of the conflicting document would be fine
      }
      assertWriterOpen(w, "commit of the second segment");
      w.addDocument(plain("3"));
      try {
        w.forceMerge(1);
      } catch (IOException | IllegalArgumentException e) {
        // reported below
      }
      assertWriterOpen(w, "forceMerge(1) of two cleanly flushed segments");
      w.close();
      assertHasIds(dir, "1", "3");
      // a new writer must be able to merge the committed index
      try (IndexWriter w2 = new IndexWriter(dir, config())) {
        try {
          w2.forceMerge(1);
        } catch (IOException | IllegalArgumentException e) {
          // reported below
        }
        assertWriterOpen(w2, "forceMerge(1) in a new writer");
      }
    }
  }

  /** addIndexes(Directory) imports a segment of the other type without a check; the next merge fails. */
  public void testAddIndexesDirectoryThenMerge() throws Exception {
    try (Directory src = newDirectory();
        Directory dir = newDirectory()) {
      try (IndexWriter ws = new IndexWriter(src, config())) {
        ws.addDocument(contextSuggest("s1", "banana"));
      }
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(suggest("1", "apple"));
      w.commit();
      try {
        w.addIndexes(src);
      } catch (IllegalArgumentException e) {
        // an up-front rejection would be fine
      }
      assertWriterOpen(w, "addIndexes(Directory)");
      w.addDocument(plain("3"));
      try {
        w.forceMerge(1);
      } catch (IOException | IllegalArgumentException e) {
        // reported below
      }
      assertWriterOpen(w, "forceMerge(1) after addIndexes(Directory)");
      w.close();
      assertHasIds(dir, "1", "3");
    }
  }
}
