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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestMergePostingsReuse extends LuceneTestCase {

  public void testReuseEnumFromEarlierMerge() throws IOException {
    PostingsFormat def = TestUtil.getDefaultPostingsFormat();
    List<Integer> docs = new ArrayList<>();
    PostingsFormat format =
        new PostingsFormat(def.getName()) {
          PostingsEnum kept;

          @Override
          public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            FieldsConsumer in = def.fieldsConsumer(state);
            return new FieldsConsumer() {
              @Override
              public void write(Fields fields, NormsProducer norms) throws IOException {
                if (state.context.context() == IOContext.Context.MERGE) {
                  TermsEnum te = fields.terms("f").iterator();
                  te.next();
                  kept = te.postings(kept, PostingsEnum.NONE);
                  docs.clear();
                  for (int d = kept.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = kept.nextDoc()) {
                    docs.add(d);
                  }
                }
                in.write(fields, norms);
              }

              @Override
              public void close() throws IOException {
                in.close();
              }
            };
          }

          @Override
          public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
            return def.fieldsProducer(state);
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysPostingsFormat(format))
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new StringField("f", "t", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      w.addDocument(doc);
      w.commit();
      w.forceMerge(1);
      w.addDocument(doc);
      w.commit();
      w.forceMerge(1);
      // expected: the second merge's docs [0, 1, 2]; actual: [0, 1, 1] (earlier merge's doc bases)
      assertEquals(List.of(0, 1, 2), docs);
    }
  }
}
