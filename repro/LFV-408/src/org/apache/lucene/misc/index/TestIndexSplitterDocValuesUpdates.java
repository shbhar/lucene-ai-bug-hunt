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
package org.apache.lucene.misc.index;

import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexSplitter.split copies a segment that has a doc-values update. The copy must read the same
 * doc values as the source, and must stay readable after an IndexWriter has opened it.
 */
public class TestIndexSplitterDocValuesUpdates extends LuceneTestCase {

  /** Source index: one segment of 4 docs, price = 10..13, then price of doc 1 updated to 999. */
  private static String buildSource(Path src) throws Exception {
    try (Directory dir = FSDirectory.open(src);
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < 4; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        doc.add(new NumericDocValuesField("price", 10 + i));
        w.addDocument(doc);
      }
      w.commit();
      w.updateNumericDocValue(new Term("id", "1"), "price", 999);
      w.commit();
    }
    return new IndexSplitter(src).infos.info(0).info.name;
  }

  private static String prices(Path path) throws Exception {
    try (Directory dir = FSDirectory.open(path);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      LeafReader leaf = reader.leaves().get(0).reader();
      NumericDocValues dv = leaf.getNumericDocValues("price");
      StringBuilder sb = new StringBuilder();
      for (int doc = 0; doc < leaf.maxDoc(); doc++) {
        sb.append(dv != null && dv.advanceExact(doc) ? Long.toString(dv.longValue()) : "none");
        sb.append(doc + 1 < leaf.maxDoc() ? "," : "");
      }
      return sb.toString();
    }
  }

  /** The copied segment must expose the same doc values as the source segment. */
  public void testCopyReadsSameDocValues() throws Exception {
    Path src = createTempDir("src");
    Path dest = createTempDir("dest");
    String segment = buildSource(src);
    assertEquals("10,999,12,13", prices(src));

    new IndexSplitter(src).split(dest, new String[] {segment});

    assertEquals("doc values of the copied segment", prices(src), prices(dest));
  }

  /** The destination must still open after an IndexWriter has been opened and closed on it. */
  public void testCopySurvivesIndexWriterOpen() throws Exception {
    Path src = createTempDir("src");
    Path dest = createTempDir("dest");
    String segment = buildSource(src);

    new IndexSplitter(src).split(dest, new String[] {segment});

    try (Directory dir = FSDirectory.open(dest)) {
      new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())).close();
    }
    // throws NoSuchFileException if the writer deleted the update files as unreferenced
    String destPrices = prices(dest);
    assertEquals(prices(src), destPrices);
  }
}
