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

import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDateRecognizerFactoryThreads extends LuceneTestCase {

  public void testTwoThreadsShareOneAnalyzer() throws Exception {
    Analyzer analyzer =
        CustomAnalyzer.builder()
            .withTokenizer("keyword")
            .addTokenFilter("dateRecognizer", "datePattern", "yyyy-MM-dd")
            .build();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Runnable analyse =
        () -> {
          try {
            for (int i = 0; i < 1_000; i++) {
              try (TokenStream ts = analyzer.tokenStream("f", "2024-01-15")) {
                ts.reset();
                while (ts.incrementToken()) {}
                ts.end();
              }
            }
          } catch (Throwable t) {
            failure.compareAndSet(null, t);
          }
        };
    Thread other = new Thread(analyse);
    other.start();
    analyse.run();
    other.join();
    analyzer.close();
    // expected: no exception (one filter per thread); actual: the shared DateFormat throws
    Throwable t = failure.get();
    if (t != null) {
      throw new AssertionError("expected no exception, but analysis threw " + t, t);
    }
  }
}
