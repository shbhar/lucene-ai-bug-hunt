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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

/**
 * Run with the Lucene 9.4.2 release jars: writes a one-segment index whose BYTE vector field "v" is
 * sparse (only even docs have a vector). Doc i has id "i"; even doc i has vector [i, i+1] and the
 * same bytes in the stored field "expected".
 */
public class GenSparseByteVectorIndex94 {
  public static void main(String[] args) throws Exception {
    Path out = Paths.get(args[0]);
    Files.createDirectories(out);
    try (FSDirectory dir = FSDirectory.open(out);
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setUseCompoundFile(false))) {
      for (int i = 0; i < 6; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        if (i % 2 == 0) {
          byte[] v = new byte[] {(byte) i, (byte) (i + 1)};
          d.add(
              new KnnVectorField(
                  "v",
                  new BytesRef(v),
                  KnnVectorField.createFieldType(
                      2, VectorEncoding.BYTE, VectorSimilarityFunction.EUCLIDEAN)));
          d.add(new StoredField("expected", v));
        }
        w.addDocument(d);
      }
      w.forceMerge(1);
      w.commit();
    }
    System.out.println("wrote " + out);
  }
}
