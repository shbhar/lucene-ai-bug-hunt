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
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDocStatsDuringFullFlush extends LuceneTestCase {

  private IndexWriter w;
  private Set<String> segmentsWrittenByCommit;
  private int maxDocWhileWritingSecondSegment = -1;

  public void testDocStatsDuringCommit() throws IOException {
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (segmentsWrittenByCommit != null
                && name.startsWith("_")
                && segmentsWrittenByCommit.add(IndexFileNames.parseSegmentName(name))
                && segmentsWrittenByCommit.size() == 2) {
              maxDocWhileWritingSecondSegment = w.getDocStats().maxDoc;
            }
            return super.createOutput(name, context);
          }
        };
    w = new IndexWriter(dir, new IndexWriterConfig());
    Iterable<Document> oneDocAddingAnother =
        () ->
            new Iterator<>() {
              boolean done;

              @Override
              public boolean hasNext() {
                return done == false;
              }

              @Override
              public Document next() {
                done = true;
                try {
                  w.addDocument(new Document());
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
                return new Document();
              }
            };
    w.addDocuments(oneDocAddingAnother);
    assertEquals(2, w.getDocStats().maxDoc);
    segmentsWrittenByCommit = new HashSet<>();
    w.commit();
    w.close();
    dir.close();
    // expected: maxDoc stays 2 while commit() writes the second segment; actual: 1
    assertEquals(2, maxDocWhileWritingSecondSegment);
  }
}
