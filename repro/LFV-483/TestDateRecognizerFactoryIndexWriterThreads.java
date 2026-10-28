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
package org.apache.lucene.analysis.miscellaneous;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Reachability check through the canonical multi-threaded consumer: one IndexWriter (documented as
 * completely thread safe) shared by two indexing threads, configured with an Analyzer built from
 * the documented dateRecognizer factory parameters. Every addDocument call is expected to succeed.
 * The control uses the same tokenizer and filter built through the public constructor with one
 * DateFormat per component.
 */
public class TestDateRecognizerFactoryIndexWriterThreads extends LuceneTestCase {

  private static final String TEXT = "2024-01-15 foo 1999-12-31 bar 2001-07-04 baz 1970-01-01";
  private static final int THREADS = 2;
  private static final int DOCS_PER_THREAD = 20_000;

  /** Returns the first exception message, or null if every document was indexed. */
  private static String indexConcurrently(Analyzer analyzer) throws Exception {
    AtomicReference<String> first = new AtomicReference<>();
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig(analyzer).setMergeScheduler(new SerialMergeScheduler()))) {
      CountDownLatch startingGun = new CountDownLatch(1);
      Thread[] threads = new Thread[THREADS];
      for (int t = 0; t < THREADS; t++) {
        final int id = t;
        threads[t] =
            new Thread(
                () -> {
                  try {
                    startingGun.await();
                    for (int i = 0; i < DOCS_PER_THREAD && first.get() == null; i++) {
                      Document doc = new Document();
                      doc.add(new TextField("body", TEXT, Field.Store.NO));
                      try {
                        w.addDocument(doc);
                      } catch (Throwable e) {
                        first.compareAndSet(
                            null, "thread " + id + " addDocument #" + i + " threw " + e);
                        return;
                      }
                    }
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                });
        threads[t].start();
      }
      startingGun.countDown();
      for (Thread t : threads) {
        t.join();
      }
      if (first.get() == null) {
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          assertEquals(THREADS * DOCS_PER_THREAD, r.numDocs());
        }
      }
    }
    return first.get();
  }

  public void testConstructorPerComponentDateFormatIndexWriter() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                t, new DateRecognizerFilter(t, new SimpleDateFormat("yyyy-MM-dd", Locale.US)));
          }
        };
    String first = indexConcurrently(a);
    a.close();
    assertNull("constructor-built analyzer failed: " + first, first);
  }

  public void testFactoryBuiltAnalyzerIndexWriter() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter("dateRecognizer", "datePattern", "yyyy-MM-dd", "locale", "en-US")
            .build();
    String first = indexConcurrently(a);
    a.close();
    assertNull("factory-built analyzer failed under a shared IndexWriter: " + first, first);
  }
}
