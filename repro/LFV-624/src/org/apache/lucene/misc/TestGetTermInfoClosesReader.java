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
package org.apache.lucene.misc;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** GetTermInfo.getTermInfo opens a DirectoryReader on the given directory and never closes it. */
public class TestGetTermInfoClosesReader extends LuceneTestCase {

  public void testGetTermInfoClosesItsReader() throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null))) {
      Document d = new Document();
      d.add(new StringField("id", "1", Field.Store.YES));
      w.addDocument(d);
    }
    PrintStream saved = System.out;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
    try {
      GetTermInfo.getTermInfo(dir, new Term("id", "1"));
    } finally {
      System.setOut(saved);
    }
    assertTrue(bytes.toString(StandardCharsets.UTF_8), bytes.toString(StandardCharsets.UTF_8).contains("doc freq = 1"));
    // MockDirectoryWrapper.close() fails if a file of the directory is still open:
    // "MockDirectoryWrapper: cannot close: there are still N open files"
    dir.close();
  }
}
