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
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Closing a FilterCodecReader must not close a wrapped reader that still has references
 * (IndexReader javadoc: "the IndexReader will not really be closed until decRef has been called for
 * all outstanding references"). Each test takes an extra reference for the wrapper, as
 * FilterLeafReader needs, and releases it itself if the wrapper did not, so the assertions hold
 * whether or not the wrapper owns a reference. A plain MMapDirectory and the default codec make the
 * symptom the one applications see.
 */
public class TestFilterCodecReaderClose extends LuceneTestCase {

  private static void index(Directory dir) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 10; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        d.add(new NumericDocValuesField("n", i));
        w.addDocument(d);
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

  /** The DirectoryReader still holds the segment, so it must keep answering. */
  public void testOpenReaderStillWorksAfterWrapperClose() throws IOException {
    try (Directory dir = new MMapDirectory(createTempDir())) {
      index(dir);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        TermQuery q = new TermQuery(new Term("id", "3"));
        assertEquals(1, new IndexSearcher(reader).count(q));
        CodecReader segment = (CodecReader) reader.leaves().get(0).reader();
        segment.incRef(); // a reference for the wrapper
        wrap(segment).close();
        if (segment.getRefCount() > 1) {
          segment.decRef(); // the wrapper did not release it
        }
        assertEquals("the DirectoryReader's own reference", 1, segment.getRefCount());
        assertEquals(
            "hits for id:3 after closing the wrapper", 1, new IndexSearcher(reader).count(q));
        assertEquals("3", reader.storedFields().document(3).get("id"));
      }
    }
  }

  /** Every reference was released exactly once, so the owner's close must be clean. */
  public void testOwnerCloseAfterWrapperClose() throws IOException {
    try (Directory dir = new MMapDirectory(createTempDir())) {
      index(dir);
      DirectoryReader reader = DirectoryReader.open(dir);
      CodecReader segment = (CodecReader) reader.leaves().get(0).reader();
      segment.incRef(); // a reference for the wrapper
      wrap(segment).close();
      if (segment.getRefCount() > 1) {
        segment.decRef(); // the wrapper did not release it
      }
      reader.close(); // must not throw
    }
  }

  /** Control: FilterLeafReader releases one reference (in.close()), so the same steps are clean. */
  public void testFilterLeafReaderControl() throws IOException {
    try (Directory dir = new MMapDirectory(createTempDir())) {
      index(dir);
      DirectoryReader reader = DirectoryReader.open(dir);
      LeafReader segment = reader.leaves().get(0).reader();
      segment.incRef(); // a reference for the wrapper
      new FilterLeafReader(segment) {
        @Override
        public CacheHelper getCoreCacheHelper() {
          return null;
        }

        @Override
        public CacheHelper getReaderCacheHelper() {
          return null;
        }
      }.close();
      assertEquals(1, segment.getRefCount());
      assertEquals(1, new IndexSearcher(reader).count(new TermQuery(new Term("id", "3"))));
      reader.close();
    }
  }
}
