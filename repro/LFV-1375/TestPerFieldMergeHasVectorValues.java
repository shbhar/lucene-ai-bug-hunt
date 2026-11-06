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
import java.util.List;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPerFieldMergeHasVectorValues extends LuceneTestCase {

  public void testMergeFieldInfosHasVectorValues() throws IOException {
    List<Boolean> seen = new ArrayList<>();
    PostingsFormat in = PostingsFormat.forName("Lucene104");
    PostingsFormat recording =
        new PostingsFormat("Lucene104") {
          @Override
          public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            FieldsConsumer c = in.fieldsConsumer(state);
            return new FieldsConsumer() {
              @Override
              public void write(Fields fields, NormsProducer norms) throws IOException {
                c.write(fields, norms);
              }

              @Override
              public void merge(MergeState mergeState, NormsProducer norms) throws IOException {
                seen.add(mergeState.mergeFieldInfos.hasVectorValues());
                c.merge(mergeState, norms);
              }

              @Override
              public void close() throws IOException {
                c.close();
              }
            };
          }

          @Override
          public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
            return in.fieldsProducer(state);
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(
                new Lucene104Codec() {
                  @Override
                  public PostingsFormat getPostingsFormatForField(String field) {
                    return recording;
                  }
                });
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 2; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", "" + i, Field.Store.NO));
        doc.add(new KnnFloatVectorField("v", new float[] {1, i}));
        w.addDocument(doc);
        w.commit();
      }
      w.forceMerge(1);
    }
    // expected: the merge view holds only "id", so hasVectorValues() is false; actual: true
    assertEquals(List.of(false), seen);
  }
}
