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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCheckIndexSortedSegmentWithoutParent extends LuceneTestCase {

  public void testBlockSegmentWithoutParentDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(new SimpleTextCodec())
              .setParentField("_parent")
              .setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document parent = new Document();
        parent.add(new StringField("id", "parent", Field.Store.NO));
        parent.add(new NumericDocValuesField("sort", 1));
        w.addDocuments(List.of(new Document(), parent));
        w.deleteDocuments(new Term("id", "parent"));
        w.forceMerge(1);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        checker.setThreadCount(1);
        checker.setInfoStream(new PrintStream(out, false, StandardCharsets.UTF_8));
        boolean clean = checker.checkIndex().clean;
        // expected: clean, as the default codec gives; actual: index sort ERROR, 1 broken segment
        assertTrue(out.toString(StandardCharsets.UTF_8), clean);
      }
    }
  }
}
