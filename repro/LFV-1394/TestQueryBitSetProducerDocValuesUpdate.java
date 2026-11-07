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
package org.apache.lucene.search.join;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.IOUtils;

public class TestQueryBitSetProducerDocValuesUpdate extends LuceneTestCase {

  public void testInPlaceDocValuesUpdate() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new NumericDocValuesField("stock", 0));
    w.addDocument(doc);
    QueryBitSetProducer producer =
        new QueryBitSetProducer(NumericDocValuesField.newSlowExactQuery("stock", 1));
    DirectoryReader reader = DirectoryReader.open(w);
    producer.getBitSet(reader.leaves().get(0));

    w.updateNumericDocValue(new Term("id", "1"), "stock", 1);
    DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
    BitSet bits = producer.getBitSet(newReader.leaves().get(0));
    // expected: doc 0 matches once stock is 1; actual: no match, the result cached for the old reader
    assertTrue(bits != null && bits.get(0));
    IOUtils.close(reader, newReader, w, dir);
  }
}
