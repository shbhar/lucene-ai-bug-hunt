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
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.suggest.BitsProducer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * Independent repro (public API only): a CompletionQuery executed through
 * IndexSearcher#search(Query, CollectorManager) with a TopSuggestDocsCollector must skip a segment
 * in which the query cannot match and return the same suggestions as SuggestIndexSearcher#suggest.
 * Compiles against 9.x, 10.x and main; an 8.x variant differs only in the test-framework imports.
 * Expected on every version: the same suggestions as SuggestIndexSearcher#suggest.
 */
public class TestHistoryCompletionQueryIndexSearcherSearch extends LuceneTestCase {

  private static final String FIELD = "suggest_field";

  /** Newest completion postings format registered on the classpath of this Lucene version. */
  private static PostingsFormat newestCompletionFormat() {
    Set<String> available = PostingsFormat.availablePostingsFormats();
    for (String name :
        new String[] {
          "Completion104", "Completion101", "Completion912", "Completion99", "Completion90", "Completion84",
          "completion"
        }) {
      if (available.contains(name)) {
        return PostingsFormat.forName(name);
      }
    }
    throw new AssertionError("no completion postings format among " + available);
  }

  private static IndexWriterConfig config(Analyzer analyzer) {
    final Codec base = Codec.getDefault();
    final PostingsFormat completion = newestCompletionFormat();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setCodec(
        new FilterCodec(base.getName(), base) {
          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                if (FIELD.equals(field)) {
                  return completion;
                }
                return ((PerFieldPostingsFormat) delegate.postingsFormat())
                    .getPostingsFormatForField(field);
              }
            };
          }
        });
    return iwc;
  }

  private static Document suggestDoc(String key, int weight) {
    Document doc = new Document();
    doc.add(new SuggestField(FIELD, key, weight));
    return doc;
  }

  private static Document plainDoc() {
    Document doc = new Document();
    doc.add(new StringField("id", "no-suggest-field", Field.Store.NO));
    return doc;
  }

  /** One collector per slice; a searcher without executor uses a single slice. */
  private static final class SuggestManager
      implements CollectorManager<TopSuggestDocsCollector, TopSuggestDocs> {
    private final int n;

    SuggestManager(int n) {
      this.n = n;
    }

    @Override
    public TopSuggestDocsCollector newCollector() {
      return new TopSuggestDocsCollector(n, false);
    }

    @Override
    public TopSuggestDocs reduce(Collection<TopSuggestDocsCollector> collectors)
        throws IOException {
      assertEquals(1, collectors.size());
      return collectors.iterator().next().get();
    }
  }

  private static Set<String> keys(TopSuggestDocs docs) {
    Set<String> keys = new TreeSet<>();
    for (TopSuggestDocs.SuggestScoreDoc sd : docs.scoreLookupDocs()) {
      keys.add(sd.key.toString() + "@" + sd.doc);
    }
    return keys;
  }

  /**
   * Segment 0 has three suggestions; segment 1 has no document with the suggest field.
   * SuggestIndexSearcher#suggest returns 3 hits; IndexSearcher#search of the same query must too.
   */
  public void testSegmentWithoutSuggestField() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config(analyzer))) {
      w.addDocument(suggestDoc("abc", 3));
      w.addDocument(suggestDoc("abd", 2));
      w.addDocument(suggestDoc("abe", 1));
      w.commit();
      w.addDocument(plainDoc());
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.leaves().size());
        PrefixCompletionQuery query = new PrefixCompletionQuery(analyzer, new Term(FIELD, "ab"));

        // control: the documented suggest path
        TopSuggestDocs expected = new SuggestIndexSearcher(reader).suggest(query, 10, false);
        assertEquals(3, expected.scoreLookupDocs().length);

        // the generic search path on the same reader
        IndexSearcher searcher = new IndexSearcher(reader);
        TopSuggestDocs actual = searcher.search(query, new SuggestManager(10));
        assertEquals(keys(expected), keys(actual));
      }
    }
    analyzer.close();
  }

  /**
   * Both segments have suggestions, but the BitsProducer filter returns MatchNoBits for segment 1.
   * Expected: the 3 suggestions of segment 0, as with SuggestIndexSearcher#suggest.
   */
  public void testFilterMatchingNothingOnOneSegment() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config(analyzer))) {
      w.addDocument(suggestDoc("abc", 3));
      w.addDocument(suggestDoc("abd", 2));
      w.addDocument(suggestDoc("abe", 1));
      w.commit();
      w.addDocument(suggestDoc("abf", 5));
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.leaves().size());
        BitsProducer onlyFirstSegment =
            new BitsProducer() {
              @Override
              public Bits getBits(LeafReaderContext context) {
                int maxDoc = context.reader().maxDoc();
                return context.ord == 0 ? new Bits.MatchAllBits(maxDoc) : new Bits.MatchNoBits(maxDoc);
              }
            };
        PrefixCompletionQuery query =
            new PrefixCompletionQuery(analyzer, new Term(FIELD, "ab"), onlyFirstSegment);

        TopSuggestDocs expected = new SuggestIndexSearcher(reader).suggest(query, 10, false);
        assertEquals(3, expected.scoreLookupDocs().length);

        IndexSearcher searcher = new IndexSearcher(reader);
        TopSuggestDocs actual = searcher.search(query, new SuggestManager(10));
        assertEquals(keys(expected), keys(actual));
      }
    }
    analyzer.close();
  }

  /**
   * Same as the first test, but the segment without the suggest field comes first and a query
   * timeout that never fires is set. Only distinct hits are compared.
   */
  public void testSegmentWithoutSuggestFieldWithTimeout() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config(analyzer))) {
      w.addDocument(plainDoc());
      w.commit();
      w.addDocument(suggestDoc("abc", 3));
      w.addDocument(suggestDoc("abd", 2));
      w.addDocument(suggestDoc("abe", 1));
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.leaves().size());
        PrefixCompletionQuery query = new PrefixCompletionQuery(analyzer, new Term(FIELD, "ab"));
        TopSuggestDocs expected = new SuggestIndexSearcher(reader).suggest(query, 10, false);
        assertEquals(3, expected.scoreLookupDocs().length);

        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setTimeout(() -> false);
        TopSuggestDocs actual = searcher.search(query, new SuggestManager(10));
        assertEquals(keys(expected), keys(actual));
        assertFalse(searcher.timedOut());
      }
    }
    analyzer.close();
  }
}
