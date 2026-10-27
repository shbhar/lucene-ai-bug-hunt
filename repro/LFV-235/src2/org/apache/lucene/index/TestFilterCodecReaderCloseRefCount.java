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
package org.apache.lucene.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * FilterCodecReader#doClose calls {@code in.doClose()} on the wrapped reader instead of releasing a
 * reference to it (FilterLeafReader#doClose calls {@code in.close()}). Closing the wrapper therefore
 * closes the wrapped reader's files while the wrapped reader still has outstanding references, and
 * the wrapped reader's own (later) close releases the same resources a second time.
 *
 * <p>IndexReader#incRef javadoc: "the IndexReader will not really be closed until decRef has been
 * called for all outstanding references". Only public API is used.
 */
public class TestFilterCodecReaderCloseRefCount extends LuceneTestCase {

  private static void index(Directory dir, boolean withDocValuesUpdate) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 10; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        d.add(new NumericDocValuesField("n", i));
        w.addDocument(d);
      }
      if (withDocValuesUpdate) {
        w.commit();
        w.updateNumericDocValue(new Term("id", "3"), "n", 33);
      }
    }
  }

  private static CodecReader wrap(CodecReader in) {
    return new FilterCodecReader(in) {
      @Override
      public CacheHelper getCoreCacheHelper() {
        return null;
      }

      @Override
      public CacheHelper getReaderCacheHelper() {
        return null;
      }
    };
  }

  /** A wrapper that holds its own reference: closing it must leave the shared reader usable. */
  public void testClosingWrapperKeepsReferencedReaderOpen() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir, false);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        CodecReader segment = (CodecReader) reader.leaves().get(0).reader();
        segment.incRef(); // a reference for the wrapper, as with FilterLeafReader
        CodecReader wrapper = wrap(segment);
        wrapper.close();
        assertTrue("the open DirectoryReader still references the segment", segment.getRefCount() >= 1);
        // the DirectoryReader is open and was never closed: it must still be usable
        assertEquals(
            "hits for id:3 in the open DirectoryReader after closing the wrapper",
            1,
            new IndexSearcher(reader).count(new TermQuery(new Term("id", "3"))));
        assertEquals("3", reader.document(3).get("id"));
        if (segment.getRefCount() > 1) {
          segment.decRef(); // the wrapper did not release the reference taken for it
        }
      }
    }
  }

  /** Closing the wrapper and then the reader that owns the segment releases the segment twice. */
  public void testClosingOwnerAfterWrapperIsClean() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir, true);
      DirectoryReader reader = DirectoryReader.open(dir);
      CodecReader segment = (CodecReader) reader.leaves().get(0).reader();
      segment.incRef(); // a reference for the wrapper, as with FilterLeafReader
      CodecReader wrapper = wrap(segment);
      wrapper.close();
      if (segment.getRefCount() > 1) {
        segment.decRef(); // the wrapper did not release the reference taken for it
      }
      reader.close(); // must not throw: every reference was released exactly once
    }
  }

  /** Control: FilterLeafReader releases a reference (in.close()), so the same sequence is clean. */
  public void testFilterLeafReaderControl() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir, true);
      DirectoryReader reader = DirectoryReader.open(dir);
      LeafReader segment = reader.leaves().get(0).reader();
      segment.incRef();
      LeafReader wrapper =
          new FilterLeafReader(segment) {
            @Override
            public CacheHelper getCoreCacheHelper() {
              return null;
            }

            @Override
            public CacheHelper getReaderCacheHelper() {
              return null;
            }
          };
      wrapper.close(); // releases the reference taken above
      assertEquals(1, segment.getRefCount());
      assertEquals(1, new IndexSearcher(reader).count(new TermQuery(new Term("id", "3"))));
      reader.close();
    }
  }
}
