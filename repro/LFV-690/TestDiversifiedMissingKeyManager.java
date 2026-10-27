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
package org.apache.lucene.misc.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Manager path: the keyless document and the key-0 document sit in different segments, and every
 * segment is its own slice, so each per-slice collector keeps its document and only
 * DiversifiedTopDocsCollectorManager#reduce can merge them. numHits = 2, maxHitsPerKey = 1.
 */
public class TestDiversifiedMissingKeyManager extends LuceneTestCase {

  static final class NumericKeys extends DiversifiedTopDocsCollector {
    NumericKeys(int numHits, int maxHitsPerKey) {
      super(numHits, maxHitsPerKey);
    }

    @Override
    protected NumericDocValues getKeys(LeafReaderContext context) {
      try {
        return DocValues.getNumeric(context.reader(), "seller");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private void check(long realKey, int expected) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document keyless = new Document();
        keyless.add(new StringField("id", "keyless", Field.Store.YES));
        w.addDocument(keyless);
        w.commit();
        Document keyed = new Document();
        keyed.add(new StringField("id", "keyed", Field.Store.YES));
        keyed.add(new NumericDocValuesField("seller", realKey));
        w.addDocument(keyed);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.leaves().size());
        IndexSearcher searcher =
            new IndexSearcher(reader, Runnable::run) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return slices(leaves, 1, 1, false);
              }
            };
        assertEquals(2, searcher.getSlices().length);
        TopDocs td =
            searcher.search(
                new MatchAllDocsQuery(),
                new DiversifiedTopDocsCollectorManager<NumericKeys>(2, 1) {
                  @Override
                  public NumericKeys newCollector() {
                    return new NumericKeys(2, 1);
                  }
                });
        assertEquals(2, td.totalHits.value());
        assertEquals("real key " + realKey, expected, td.scoreDocs.length);
      }
    }
  }

  public void testReduceKeylessVsKeyZeroExpect2() throws Exception {
    check(0L, 2);
  }

  public void testReduceKeylessVsKeySevenObserved() throws Exception {
    check(7L, 2);
  }
}
