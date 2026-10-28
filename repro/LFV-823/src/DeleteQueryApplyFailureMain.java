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
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Version-portable main() form of the repro (works on 6.x .. 11.x): exit 1 if a delete query that
 * deleteDocuments(Query) accepted makes the next commit fail / the writer close / acknowledged
 * documents disappear; exit 0 if the writer stays open and all 10 acknowledged documents commit.
 */
public class DeleteQueryApplyFailureMain {
  static Document doc(int i) {
    Document d = new Document();
    d.add(new StringField("id", "d" + i, Field.Store.NO));
    return d;
  }

  public static void main(String[] args) throws Exception {
    Path tmp = Files.createTempDirectory("delq");
    int exit;
    try (Directory dir = FSDirectory.open(tmp)) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new KeywordAnalyzer()));
      for (int i = 0; i < 6; i++) w.addDocument(doc(i));
      w.commit();
      for (int i = 6; i < 10; i++) w.addDocument(doc(i));
      PhraseQuery q =
          new PhraseQuery.Builder().add(new Term("id", "d1")).add(new Term("id", "d2")).build();
      String call = "returned normally";
      try {
        w.deleteDocuments(q);
      } catch (Throwable t) {
        call = "threw " + t;
      }
      String commit = "ok";
      try {
        w.commit();
      } catch (Throwable t) {
        commit = "threw " + t;
      }
      boolean open = w.isOpen();
      Throwable tragic = w.getTragicException();
      String retry = "-";
      if (open) {
        try {
          w.commit();
          retry = "ok";
        } catch (Throwable t) {
          retry = "threw " + t;
        }
        try {
          w.close();
        } catch (Throwable t) {
          retry += "; close threw " + t;
          w.rollback();
        }
      }
      int committed;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        committed = r.numDocs();
      }
      System.out.println("deleteDocuments: " + call);
      System.out.println("first commit: " + commit);
      System.out.println("writer open after commit: " + open + " tragic=" + tragic);
      System.out.println("retry commit: " + retry);
      System.out.println("committed docs: " + committed + " of 10 acknowledged");
      boolean ok = commit.equals("ok") && open && committed == 10;
      System.out.println(ok ? "RESULT: OK" : "RESULT: BUG");
      exit = ok ? 0 : 1;
    }
    for (Path p : (Iterable<Path>) Files.walk(tmp).sorted(java.util.Comparator.reverseOrder())::iterator) {
      Files.deleteIfExists(p);
    }
    System.exit(exit);
  }
}
