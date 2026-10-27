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

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterCodecReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Production-like check (default codec, FSDirectory, no test-framework wrappers, public API only):
 * after a FilterCodecReader around a leaf of an open DirectoryReader is closed, with the leaf's
 * reference count still covering the DirectoryReader, the DirectoryReader must keep returning the
 * same hits and close cleanly. Exits 1 on a violation.
 */
public class FilterCodecReaderCloseMain {

  static FilterCodecReader wrap(CodecReader delegate) {
    return new FilterCodecReader(delegate) {
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

  public static void main(String[] args) throws Exception {
    Path path = Files.createTempDirectory("fcr-close");
    int violations = 0;
    try (Directory dir = FSDirectory.open(path)) {
      System.out.println("directory=" + dir.getClass().getSimpleName());
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
          doc.add(new NumericDocValuesField("dv", i));
          w.addDocument(doc);
        }
        w.forceMerge(1);
        w.updateNumericDocValue(new Term("id", "3"), "dv", 42L);
        w.commit();
      }

      DirectoryReader r = DirectoryReader.open(dir);
      CodecReader leaf = (CodecReader) r.leaves().get(0).reader();
      IndexSearcher s = new IndexSearcher(r);
      TermQuery q = new TermQuery(new Term("id", "3"));
      int before = s.count(q);
      System.out.println("codec=" + org.apache.lucene.codecs.Codec.getDefault().getName());
      System.out.println("hits(id:3) before wrapper close = " + before);

      leaf.incRef();
      int refsBeforeWrap = leaf.getRefCount();
      wrap(leaf).close();
      int refsAfterWrap = leaf.getRefCount();
      System.out.println("leaf refCount before/after wrapper close = " + refsBeforeWrap + "/" + refsAfterWrap);
      if (refsAfterWrap == refsBeforeWrap) {
        leaf.decRef(); // wrapper did not release our extra reference; release it ourselves
      }
      System.out.println("leaf refCount now (owner only) = " + leaf.getRefCount());

      int after;
      try {
        after = new IndexSearcher(r).count(q);
      } catch (Throwable t) {
        after = -1;
        System.out.println("search after wrapper close threw " + t);
      }
      System.out.println("hits(id:3) after wrapper close = " + after);
      if (after != before) {
        violations++;
        System.out.println("VIOLATION: still-open DirectoryReader changed its answer");
      }
      try {
        String id = r.storedFields().document(3).get("id");
        System.out.println("stored id of doc 3 = " + id);
      } catch (Throwable t) {
        violations++;
        System.out.println("VIOLATION: stored fields on still-open DirectoryReader threw " + t);
      }
      try {
        r.close();
        System.out.println("DirectoryReader.close() ok");
      } catch (Throwable t) {
        violations++;
        System.out.println("VIOLATION: DirectoryReader.close() threw " + t);
      }
    }
    System.out.println("violations=" + violations);
    System.exit(violations == 0 ? 0 : 1);
  }
}
