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

import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class TestCustomTermFreqBelowOne extends LuceneTestCase {

  public static final class UncheckedFreqAttributeImpl extends AttributeImpl
      implements TermFrequencyAttribute {
    private int freq = 1;

    @Override
    public void setTermFrequency(int termFrequency) {
      freq = termFrequency;
    }

    @Override
    public int getTermFrequency() {
      return freq;
    }

    @Override
    public void clear() {
      freq = 1;
    }

    @Override
    public void copyTo(AttributeImpl target) {
      ((TermFrequencyAttribute) target).setTermFrequency(freq);
    }

    @Override
    public void reflectWith(AttributeReflector reflector) {
      reflector.reflect(TermFrequencyAttribute.class, "termFrequency", freq);
    }
  }

  private static final class FooWithFreq extends TokenStream {
    private final int freq;
    private final TermFrequencyAttribute freqAtt;
    private final CharTermAttribute termAtt;
    private boolean done;

    FooWithFreq(int freq) {
      addAttributeImpl(new UncheckedFreqAttributeImpl());
      freqAtt = addAttribute(TermFrequencyAttribute.class);
      termAtt = addAttribute(CharTermAttribute.class);
      this.freq = freq;
    }

    @Override
    public boolean incrementToken() {
      if (done) {
        return false;
      }
      clearAttributes();
      termAtt.append("foo");
      freqAtt.setTermFrequency(freq);
      return done = true;
    }

    @Override
    public void reset() {
      done = false;
    }
  }

  public void testZeroCustomTermFrequency() throws Exception {
    FieldType type = new FieldType();
    type.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        // expected: addDocument rejects frequency 0 with IllegalArgumentException; actual: it is
        // indexed and the committed segment fails CheckIndex ("freq 0 is out of bounds")
        try {
          w.addDocument(List.of(new Field("f", new FooWithFreq(0), type)));
        } catch (IllegalArgumentException expected) {
        }
        w.addDocument(List.of(new Field("f", new FooWithFreq(2), type)));
        w.commit();
      }
      TestUtil.checkIndex(dir);
    }
  }
}
