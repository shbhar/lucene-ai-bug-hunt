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
package org.apache.lucene.codecs.perfield;

import java.io.IOException;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestPerFieldEqualFormatNames extends LuceneTestCase {

  abstract static class EqualBySetting extends PostingsFormat {
    final int setting;
    final PostingsFormat delegate = TestUtil.getDefaultPostingsFormat();

    EqualBySetting(String name, int setting) {
      super(name);
      this.setting = setting;
    }

    @Override
    public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
      return delegate.fieldsConsumer(state);
    }

    @Override
    public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
      return delegate.fieldsProducer(state);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof EqualBySetting other && other.setting == setting;
    }

    @Override
    public int hashCode() {
      return setting;
    }
  }

  public static final class EqA extends EqualBySetting {
    public EqA() {
      super("EqA", 1);
    }
  }

  public static final class EqB extends EqualBySetting {
    public EqB() {
      this(1);
    }

    EqB(int setting) {
      super("EqB", setting);
    }
  }

  // Expected: commit rejects y (equal to x's format, other name) as it does without field a, or y
  // is readable; actual: docFreq(y) == 0.
  public void testEqualFormatsWithDifferentNames() throws Exception {
    PostingsFormat a = new EqB(2), x = new EqA(), y = new EqB();
    Codec codec =
        new FilterCodec(TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
          final PostingsFormat postings =
              new PerFieldPostingsFormat() {
                @Override
                public PostingsFormat getPostingsFormatForField(String field) {
                  return field.equals("a") ? a : field.equals("x") ? x : y;
                }
              };

          @Override
          public PostingsFormat postingsFormat() {
            return postings;
          }
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
      Document doc = new Document();
      for (String field : new String[] {"a", "x", "y"}) {
        doc.add(new StringField(field, "v", Field.Store.NO));
      }
      w.addDocument(doc);
      try {
        w.commit();
      } catch (IllegalStateException rejected) {
        w.rollback();
        return;
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.docFreq(new Term("y", "v")));
      }
    }
  }
}
