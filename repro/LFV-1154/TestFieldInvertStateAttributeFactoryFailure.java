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

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Attribute;
import org.apache.lucene.util.AttributeFactory;
import org.apache.lucene.util.AttributeImpl;

public class TestFieldInvertStateAttributeFactoryFailure extends LuceneTestCase {

  public void testFactoryFailureDoesNotShiftLaterPositions() throws Exception {
    AttributeFactory factory =
        new AttributeFactory() {
          boolean failed;

          @Override
          public AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass) {
            if (attClass == TermFrequencyAttribute.class && failed == false) {
              failed = true;
              throw new IllegalStateException("transient factory failure");
            }
            return AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY.createAttributeInstance(attClass);
          }
        };
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new StandardTokenizer(factory));
          }
        };
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Tokenizer tokenizer = new StandardTokenizer();
      tokenizer.setReader(new StringReader("x the the"));
      Document doc0 = new Document();
      doc0.add(new TextField("f", new StopFilter(tokenizer, StopFilter.makeStopSet("the"))));
      w.addDocument(doc0);
      Document doc = new Document();
      doc.add(new TextField("f", "a b", Field.Store.NO));
      expectThrows(IllegalStateException.class, () -> w.addDocument(doc));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // expected: the re-added "a b" is at positions 0,1 and matches; actual: positions 1,3, 0 hits
        assertEquals(1, new IndexSearcher(r).count(new PhraseQuery("f", "a", "b")));
      }
    }
    analyzer.close();
  }
}
