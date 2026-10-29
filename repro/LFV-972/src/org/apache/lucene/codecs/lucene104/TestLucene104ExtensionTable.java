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
package org.apache.lucene.codecs.lucene104;

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLucene104ExtensionTable extends LuceneTestCase {

  private static final Set<String> SUMMARY_OF_FILE_EXTENSIONS_TABLE =
      Set.of(
          "si", "cfs", "cfe", "fnm", "fdx", "fdt", "tim", "tip", "doc", "pos", "pay", "nvd", "nvm",
          "dvd", "dvm", "tvx", "tvd", "liv", "kdd", "kdi", "kdm", "vec", "vem", "veq", "vex");

  public void testTableListsEveryExtensionTheDefaultCodecWrites() throws Exception {
    FieldType storedWithTermVectors = new FieldType(StringField.TYPE_STORED);
    storedWithTermVectors.setStoreTermVectors(true);
    Document doc = new Document();
    doc.add(new Field("id", "1", storedWithTermVectors));
    doc.add(new NumericDocValuesField("dv", 1));
    doc.add(new KnnFloatVectorField("vector", new float[] {1, 2}));
    Set<String> missing = new TreeSet<>();
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(new Lucene104Codec()).setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc);
      }
      for (String file : dir.listAll()) {
        String extension = IndexFileNames.getExtension(file);
        if (file.startsWith(IndexFileNames.SEGMENTS) == false
            && SUMMARY_OF_FILE_EXTENSIONS_TABLE.contains(extension) == false) {
          missing.add(extension);
        }
      }
    }
    // expected: the table lists every written extension; actual: 7 extensions are missing
    assertEquals("written extensions missing from the extensions table", Set.of(), missing);
  }
}
