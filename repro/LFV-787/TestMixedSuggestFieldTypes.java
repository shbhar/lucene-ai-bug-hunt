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
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * SuggestField and ContextSuggestField must not be mixed under one field name, and {@link
 * TestContextSuggestField#testMixedSuggestFields} expects an IllegalArgumentException for it. These
 * tests check that the rejection behaves like input validation: the IndexWriter stays open, the
 * other documents survive, and segments that flushed and committed without error can be merged.
 */
public class TestMixedSuggestFieldTypes extends LuceneTestCase {

  private static final String FIELD = "suggest_field";

  /** The default codec with FIELD routed to the completion postings format. */
  private static IndexWriterConfig iwc(MergePolicy mergePolicy) {
    Codec codec =
        new FilterCodec(TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return FIELD.equals(field)
                    ? completion
                    : ((PerFieldPostingsFormat) delegate.postingsFormat())
                        .getPostingsFormatForField(field);
              }
            };
          }
        };
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(codec);
    iwc.setMergePolicy(mergePolicy);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static Document plain(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    return doc;
  }

  private static Document suggest(String id, String value) {
    Document doc = plain(id);
    doc.add(new SuggestField(FIELD, value, 1));
    return doc;
  }

  private static Document contextSuggest(String id, String value) {
    Document doc = plain(id);
    doc.add(new ContextSuggestField(FIELD, value, 1, "ctx"));
    return doc;
  }

  private static void assertOpen(IndexWriter w, String step) {
    assertNull(step + " closed the IndexWriter: " + w.getTragicException(), w.getTragicException());
    assertTrue(step + " closed the IndexWriter", w.isOpen());
  }

  private static void assertIds(Directory dir, String... ids) throws IOException {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      for (String id : ids) {
        assertEquals("document " + id, 1, searcher.count(new TermQuery(new Term("id", id))));
      }
    }
  }

  /** Two segments, one per type, flush and commit without error; merging them closes the writer. */
  public void testForceMergeOfCommittedSegments() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(new LogDocMergePolicy()));
      w.addDocument(suggest("1", "apple"));
      w.commit();
      w.addDocument(contextSuggest("2", "apricot"));
      w.commit(); // no error: two committed segments, and CheckIndex finds no problem
      TestUtil.checkIndex(dir);
      w.addDocument(plain("3"));
      try {
        w.forceMerge(1);
      } catch (IOException | IllegalArgumentException _) {
        // a rejected merge would be acceptable; a closed writer is not
      }
      assertOpen(w, "forceMerge(1)");
      w.close();
      assertIds(dir, "1", "2", "3");
    }
  }

  /** The committed two-segment index stays unmergeable: a new writer's merge closes it too. */
  public void testNewWriterCannotMergeCommittedIndex() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc(new LogDocMergePolicy()))) {
        w.addDocument(suggest("1", "apple"));
        w.commit();
        w.addDocument(contextSuggest("2", "apricot"));
      } // close() commits the second segment without error
      TestUtil.checkIndex(dir);
      IndexWriter w2 = new IndexWriter(dir, iwc(new LogDocMergePolicy()));
      w2.addDocument(plain("3"));
      try {
        w2.forceMerge(1);
      } catch (IOException | IllegalArgumentException _) {
        // a rejected merge would be acceptable; a closed writer is not
      }
      assertOpen(w2, "forceMerge(1) in a new writer");
      w2.close();
      assertIds(dir, "1", "2", "3");
    }
  }

  /** No merge call: the merge that the merge policy picks after the second commit closes it. */
  public void testNaturalMergeAfterCommit() throws Exception {
    LogDocMergePolicy mergePolicy = new LogDocMergePolicy();
    mergePolicy.setMergeFactor(2); // two segments are enough for a merge
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(mergePolicy));
      w.addDocument(suggest("1", "apple"));
      w.commit();
      w.addDocument(contextSuggest("2", "apricot"));
      try {
        w.commit();
      } catch (IllegalArgumentException _) {
        // reported below
      }
      assertOpen(w, "the merge after commit()");
      w.close();
      assertIds(dir, "1", "2");
    }
  }

  /** Two accepted documents, one per type, buffered next to an unrelated document. */
  public void testOneBufferedSegment() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(NoMergePolicy.INSTANCE));
      w.addDocument(plain("0"));
      w.commit();
      w.addDocument(plain("1"));
      w.addDocument(suggest("2", "apple"));
      try {
        w.addDocument(contextSuggest("3", "apricot"));
        w.commit();
      } catch (IllegalArgumentException _) {
        // rejecting the conflicting document would be acceptable
      }
      assertOpen(w, "commit()");
      w.close();
      assertIds(dir, "0", "1", "2");
    }
  }

  /** Upstream's input in testMixedSuggestFields: one document with both types. */
  public void testOneDocumentWithBothTypes() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(NoMergePolicy.INSTANCE));
      w.addDocument(plain("0"));
      w.commit();
      w.addDocument(plain("1"));
      Document both = plain("2");
      both.add(new SuggestField(FIELD, "suggestion1", 4));
      both.add(new ContextSuggestField(FIELD, "suggestion2", 3));
      try {
        w.addDocument(both);
        w.commit();
      } catch (IllegalArgumentException _) {
        // expected by testMixedSuggestFields
      }
      assertOpen(w, "commit()");
      w.close();
      assertIds(dir, "0", "1");
    }
  }

  /** addIndexes(Directory) imports a segment of the other type; the next merge fails. */
  public void testAddIndexesDirectoryThenMerge() throws Exception {
    try (Directory other = newDirectory();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(other, iwc(new LogDocMergePolicy()))) {
        w.addDocument(contextSuggest("2", "apricot"));
      }
      IndexWriter w = new IndexWriter(dir, iwc(new LogDocMergePolicy()));
      w.addDocument(suggest("1", "apple"));
      w.commit();
      try {
        w.addIndexes(other);
      } catch (IllegalArgumentException _) {
        // rejecting the import would be acceptable
      }
      assertOpen(w, "addIndexes(Directory)");
      try {
        w.forceMerge(1);
      } catch (IOException | IllegalArgumentException _) {
        // a rejected merge would be acceptable; a closed writer is not
      }
      assertOpen(w, "forceMerge(1) after addIndexes(Directory)");
      w.close();
      assertIds(dir, "1");
    }
  }

  /** Control: the same two-segment merge with ContextSuggestField only. */
  public void testControlSingleType() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(new LogDocMergePolicy()));
      w.addDocument(contextSuggest("1", "apple"));
      w.commit();
      w.addDocument(contextSuggest("2", "apricot"));
      w.commit();
      w.addDocument(plain("3"));
      w.forceMerge(1);
      assertOpen(w, "forceMerge(1)");
      w.close();
      assertIds(dir, "1", "2", "3");
    }
  }

  /** Control: once every document of one type is deleted, the two segments merge again. */
  public void testControlMergeAfterDeletingOneType() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(new LogDocMergePolicy()));
      w.addDocument(suggest("1", "apple"));
      w.commit();
      w.addDocument(contextSuggest("2", "apricot"));
      w.commit();
      w.deleteDocuments(new Term("id", "2"));
      w.forceMerge(1);
      assertOpen(w, "forceMerge(1) after deleting the ContextSuggestField document");
      w.close();
      assertIds(dir, "1");
    }
  }
}
