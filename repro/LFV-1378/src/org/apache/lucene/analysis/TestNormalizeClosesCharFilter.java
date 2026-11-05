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
package org.apache.lucene.analysis;

import java.io.IOException;
import java.io.Reader;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNormalizeClosesCharFilter extends LuceneTestCase {

  // Expected: normalize() closes the CharFilter from initReaderForNormalization; actual: never closed.
  public void testNormalizeClosesCharFilter() {
    boolean[] closed = new boolean[1];
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new StandardTokenizer());
          }

          @Override
          protected Reader initReaderForNormalization(String fieldName, Reader reader) {
            return new CharFilter(reader) {
              @Override
              public int read(char[] cbuf, int off, int len) throws IOException {
                return input.read(cbuf, off, len);
              }

              @Override
              protected int correct(int currentOff) {
                return currentOff;
              }

              @Override
              public void close() throws IOException {
                closed[0] = true;
                super.close();
              }
            };
          }
        };
    assertEquals("foo", analyzer.normalize("f", "foo").utf8ToString());
    assertTrue("normalization CharFilter was not closed", closed[0]);
    analyzer.close();
  }
}
