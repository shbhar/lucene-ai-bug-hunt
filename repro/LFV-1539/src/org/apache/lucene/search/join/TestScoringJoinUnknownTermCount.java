package org.apache.lucene.search.join;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestScoringJoinUnknownTermCount extends LuceneTestCase {

  // expected: 1 hit; actual: IllegalArgumentException "cost must be >= 0, got -2"
  public void testScoringJoinWhenTermCountIsUnknown() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document from = new Document();
        from.add(new SortedDocValuesField("fromKey", new BytesRef("k")));
        w.addDocument(from);
        Document to = new Document();
        to.add(new StringField("toKey", "k", Field.Store.NO));
        w.addDocument(to);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader unknownTermCount =
            new FilterLeafReader(getOnlyLeafReader(reader)) {
              @Override
              public Terms terms(String field) {
                Terms terms = in.terms(field);
                return terms == null
                    ? null
                    : new FilterTerms(terms) {
                      @Override
                      public long size() {
                        return -1;
                      }
                    };
              }

              @Override
              public CacheHelper getCoreCacheHelper() {
                return null;
              }

              @Override
              public CacheHelper getReaderCacheHelper() {
                return null;
              }
            };
        IndexSearcher searcher = new IndexSearcher(unknownTermCount);
        Query fromQuery = new FieldExistsQuery("fromKey");
        Query join =
            JoinUtil.createJoinQuery("fromKey", false, "toKey", fromQuery, searcher, ScoreMode.Max);
        assertEquals(1, searcher.search(join, 10).scoreDocs.length);
      }
    }
  }
}
