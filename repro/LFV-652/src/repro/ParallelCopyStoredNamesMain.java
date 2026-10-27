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
package repro;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.index.SlowCodecReaderWrapper;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Release-history variant (Lucene 5.0 to 9.x API): addIndexes(SlowCodecReaderWrapper.wrap(new
 * ParallelLeafReader(a, b))) into an empty index. Exit 1 if the copy stores b's field under a's
 * field name, 0 if the copy is correct.
 */
public class ParallelCopyStoredNamesMain {
  static IndexWriterConfig iwc() {
    return new IndexWriterConfig(
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            throw new UnsupportedOperationException("only stored fields are indexed");
          }
        });
  }

  static void add(Directory dir, String field, String value) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, iwc())) {
      Document doc = new Document();
      doc.add(new StoredField(field, value));
      w.addDocument(doc);
    }
  }

  public static void main(String[] args) throws Exception {
    Path tmp = Files.createTempDirectory("parallel-copy");
    try (Directory a = FSDirectory.open(tmp.resolve("a"));
        Directory b = FSDirectory.open(tmp.resolve("b"));
        Directory target = FSDirectory.open(tmp.resolve("target"))) {
      add(a, "title", "Lucene in Action");
      add(b, "price", "42");
      try (DirectoryReader ra = DirectoryReader.open(a);
          DirectoryReader rb = DirectoryReader.open(b)) {
        ParallelLeafReader parallel =
            new ParallelLeafReader(false, ra.leaves().get(0).reader(), rb.leaves().get(0).reader());
        System.out.println("parallel reader: " + parallel.document(0));
        try (IndexWriter w = new IndexWriter(target, iwc())) {
          w.addIndexes(SlowCodecReaderWrapper.wrap(parallel));
        }
        parallel.close();
      }
      try (DirectoryReader copy = DirectoryReader.open(target)) {
        Document doc = copy.document(0);
        System.out.println("copy:            " + doc);
        boolean ok = "42".equals(doc.get("price")) && doc.getValues("title").length == 1;
        System.out.println(ok ? "RESULT copy correct" : "RESULT BUG: stored field renamed in copy");
        System.exit(ok ? 0 : 1);
      }
    }
  }
}
