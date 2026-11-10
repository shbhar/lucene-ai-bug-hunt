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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestPerFieldMergeFieldInfoByNumber extends LuceneTestCase {

  public void testFieldInfoByNumberOfFieldMissingFromSegment() throws IOException {
    List<FieldInfo> lookups = new ArrayList<>();
    PostingsFormat delegate = TestUtil.getDefaultPostingsFormat();
    PostingsFormat format =
        new PostingsFormat(delegate.getName()) {
          @Override
          public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            FieldsConsumer in = delegate.fieldsConsumer(state);
            return new FieldsConsumer() {
              @Override
              public void write(Fields fields, NormsProducer norms) throws IOException {
                in.write(fields, norms);
              }

              @Override
              public void merge(MergeState mergeState, NormsProducer norms) throws IOException {
                int b = mergeState.mergeFieldInfos.fieldInfo("b").number;
                for (FieldInfos segment : mergeState.fieldInfos) {
                  if (segment.fieldInfo("b") == null) {
                    lookups.add(segment.fieldInfo(b));
                  }
                }
                in.merge(mergeState, norms);
              }

              @Override
              public void close() throws IOException {
                in.close();
              }
            };
          }

          @Override
          public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
            return delegate.fieldsProducer(state);
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysPostingsFormat(format))
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StringField("a", "x", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      doc.add(new StringField("b", "y", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      w.forceMerge(1);
    }
    // expected: null for b's number in the segment without b; actual: NullPointerException
    assertEquals(Collections.singletonList(null), lookups);
  }
}
