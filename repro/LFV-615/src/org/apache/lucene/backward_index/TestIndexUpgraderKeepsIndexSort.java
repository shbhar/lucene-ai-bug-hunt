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
package org.apache.lucene.backward_index;

import java.io.InputStream;
import java.nio.file.Path;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexUpgrader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexUpgrader should upgrade the segment format and leave the index's own settings alone.
 *
 * <p>Uses Lucene's own backward-compatibility fixtures from
 * lucene/backward-codecs/src/test/org/apache/lucene/backward_index/: sorted.9.10.0.zip (written by
 * Lucene 9.10.0, index sort {@code <long: "dateDV">!}, no parent field) and sorted.10.5.2.zip
 * (written by 10.5.2, same sort, parent field "___parent"). Both must be on the test classpath.
 */
public class TestIndexUpgraderKeepsIndexSort extends LuceneTestCase {

  private Path unzip(String name) throws Exception {
    Path path = createTempDir(name);
    try (InputStream in = getClass().getResourceAsStream(name)) {
      // the fixtures live in lucene/backward-codecs/src/test/org/apache/lucene/backward_index/
      assumeTrue("fixture " + name + " not on the test classpath", in != null);
      TestUtil.unzip(in, path);
    }
    return path;
  }

  private static Sort indexSort(Directory dir) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      return reader.leaves().get(0).reader().getMetaData().sort();
    }
  }

  /** The upgraded segments must keep the index sort. */
  public void testUpgradedSegmentsKeepIndexSort() throws Exception {
    try (Directory dir = newFSDirectory(unzip("sorted.9.10.0.zip"))) {
      Sort sort = indexSort(dir);
      assertEquals("<long: \"dateDV\">!", sort.toString());

      new IndexUpgrader(dir).upgrade(); // what 'java org.apache.lucene.index.IndexUpgrader dir' runs

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(50, reader.numDocs());
        for (LeafReaderContext ctx : reader.leaves()) {
          assertEquals("index sort of " + ctx.reader(), sort, ctx.reader().getMetaData().sort());
        }
      }
    }
  }

  /** After the upgrade, the application's writer, configured with the index's sort, must open it. */
  public void testWriterWithTheIndexSortOpensUpgradedIndex() throws Exception {
    try (Directory dir = newFSDirectory(unzip("sorted.9.10.0.zip"))) {
      Sort sort = indexSort(dir);
      // works before the upgrade:
      new IndexWriter(dir, new IndexWriterConfig(null).setIndexSort(sort)).rollback();

      new IndexUpgrader(dir).upgrade();

      // fails after it with "cannot change previous indexSort=null ... to new indexSort=<long: "dateDV">!"
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null).setIndexSort(sort))) {
        w.commit();
      }
    }
  }

  /** The command-line upgrader must be able to upgrade an index that has a parent field. */
  public void testUpgraderHandlesParentField() throws Exception {
    try (Directory dir = newFSDirectory(unzip("sorted.10.5.2.zip"))) {
      // throws IllegalArgumentException: can't add field [___parent] as parent document field;
      // this IndexWriter has no parent document field configured
      new IndexUpgrader(dir).upgrade();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(50, reader.numDocs());
      }
    }
  }
}
