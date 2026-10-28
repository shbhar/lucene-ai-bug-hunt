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
package org.apache.lucene.search.suggest.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Release-portable main() version of TestCompletionMixedTypesClosesWriter (same four scenarios),
 * for every Lucene release that has ContextSuggestField. Uses only public API, the default codec
 * and the newest completion postings format the release registers. Prints one line per step and
 * exits 1 when any scenario closes the IndexWriter or loses an accepted document, 0 otherwise.
 * With the argument "control" every ContextSuggestField becomes a SuggestField (expected exit 0).
 */
public class CompletionMixedTypesMain {

  private static final PostingsFormat COMPLETION = completionFormat();
  private static final List<String> problems = new ArrayList<>();
  private static Path root;
  // "control" argument: every ContextSuggestField is replaced by a SuggestField (no type mix)
  private static boolean control;

  private static PostingsFormat completionFormat() {
    String[] names = {
      "Completion104", "Completion101", "Completion912", "Completion99", "Completion90",
      "Completion84", "completion"
    };
    for (String name : names) {
      if (PostingsFormat.availablePostingsFormats().contains(name)) {
        return PostingsFormat.forName(name);
      }
    }
    throw new AssertionError("no completion postings format");
  }

  private static IndexWriterConfig config() {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    final Codec base = Codec.getDefault();
    iwc.setCodec(
        new FilterCodec(base.getName(), base) {
          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return "suggest".equals(field)
                    ? COMPLETION
                    : ((PerFieldPostingsFormat) base.postingsFormat())
                        .getPostingsFormatForField(field);
              }
            };
          }
        });
    iwc.setMergePolicy(new LogDocMergePolicy());
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static Directory newDir(String name) throws IOException {
    return FSDirectory.open(Files.createDirectory(root.resolve(name)));
  }

  private static Document plain(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  private static Document suggest(String id, String value) {
    Document doc = plain(id);
    doc.add(new SuggestField("suggest", value, 1));
    return doc;
  }

  private static Document contextSuggest(String id, String value) {
    Document doc = plain(id);
    doc.add(control ? new SuggestField("suggest", value, 1) : new ContextSuggestField("suggest", value, 1, "ctx"));
    return doc;
  }

  private static String describe(Throwable t) {
    return t == null ? "none" : t.getClass().getName() + ": " + t.getMessage();
  }

  private static void step(String scenario, String step, Throwable thrown) {
    System.out.println("  [" + scenario + "] " + step + " -> threw " + describe(thrown));
  }

  private static void checkOpen(String scenario, IndexWriter w, String step) {
    Throwable tragic = w.getTragicException();
    boolean open = w.isOpen();
    System.out.println(
        "  [" + scenario + "] after " + step + ": isOpen=" + open + " tragic=" + describe(tragic));
    if (tragic != null || !open) {
      problems.add(scenario + ": " + step + " closed the IndexWriter (tragic=" + describe(tragic) + ")");
    }
  }

  private static void closeQuietly(String scenario, IndexWriter w) {
    try {
      w.close();
    } catch (Throwable t) {
      step(scenario, "close()", t);
    }
  }

  private static void checkIds(String scenario, Directory dir, String... ids) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      StringBuilder sb = new StringBuilder();
      for (String id : ids) {
        int c = s.count(new TermQuery(new Term("id", id)));
        sb.append(id).append('=').append(c).append(' ');
        if (c != 1) {
          problems.add(scenario + ": accepted document id=" + id + " is not in the committed index");
        }
      }
      System.out.println(
          "  [" + scenario + "] committed index: maxDoc=" + r.maxDoc() + " ids " + sb.toString().trim());
    }
  }

  /** Scenario 1: two accepted documents, one per type, in one in-memory segment. */
  static void twoDocumentsInOneSegment() throws IOException {
    String sc = "S1 two docs one segment";
    try (Directory dir = newDir("s1")) {
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(plain("committed"));
      w.commit();
      w.addDocument(plain("1"));
      w.addDocument(suggest("2", "apple"));
      Throwable thrown = null;
      try {
        w.addDocument(contextSuggest("3", "banana"));
        w.commit();
      } catch (Throwable t) {
        thrown = t;
      }
      step(sc, "addDocument(ContextSuggestField) + commit()", thrown);
      checkOpen(sc, w, "commit");
      if (w.isOpen()) {
        w.commit();
      }
      closeQuietly(sc, w);
      checkIds(sc, dir, "committed", "1", "2");
    }
  }

  /** Scenario 2: upstream's one-document mix (testMixedSuggestFields) plus an unrelated document. */
  static void oneDocumentWithBothTypes() throws IOException {
    String sc = "S2 one doc both types";
    try (Directory dir = newDir("s2")) {
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(plain("committed"));
      w.commit();
      w.addDocument(plain("1"));
      Document both = plain("2");
      both.add(new SuggestField("suggest", "apple", 4));
      both.add(control ? new SuggestField("suggest", "banana", 3) : new ContextSuggestField("suggest", "banana", 3));
      Throwable thrown = null;
      try {
        w.addDocument(both);
        w.commit();
      } catch (Throwable t) {
        thrown = t;
      }
      step(sc, "addDocument(both) + commit()", thrown);
      checkOpen(sc, w, "commit of the mixed document");
      if (w.isOpen()) {
        w.commit();
      }
      closeQuietly(sc, w);
      checkIds(sc, dir, "committed", "1");
    }
  }

  /** Scenario 3: two cleanly flushed segments, one per type; every merge of the pair. */
  static void twoCleanSegmentsPoisonEveryMerge() throws IOException {
    String sc = "S3 two clean segments + merge";
    try (Directory dir = newDir("s3")) {
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(suggest("1", "apple"));
      w.commit();
      Throwable thrown = null;
      try {
        w.addDocument(contextSuggest("2", "banana"));
        w.commit();
      } catch (Throwable t) {
        thrown = t;
      }
      step(sc, "addDocument(ContextSuggestField) + commit() (second segment)", thrown);
      checkOpen(sc, w, "commit of the second segment");
      if (w.isOpen()) {
        w.addDocument(plain("3"));
        thrown = null;
        try {
          w.forceMerge(1);
        } catch (Throwable t) {
          thrown = t;
        }
        step(sc, "addDocument(plain 3) + forceMerge(1)", thrown);
        checkOpen(sc, w, "forceMerge(1) of two cleanly flushed segments");
      }
      closeQuietly(sc, w);
      checkIds(sc, dir, "1", "3");
      IndexWriter w2 = new IndexWriter(dir, config());
      thrown = null;
      try {
        w2.forceMerge(1);
      } catch (Throwable t) {
        thrown = t;
      }
      step(sc, "new IndexWriter on the committed index + forceMerge(1)", thrown);
      checkOpen(sc, w2, "forceMerge(1) in a new writer");
      closeQuietly(sc, w2);
    }
  }

  /** Scenario 4: addIndexes(Directory) imports a segment of the other type; the next merge. */
  static void addIndexesDirectoryThenMerge() throws IOException {
    String sc = "S4 addIndexes(Directory) + merge";
    try (Directory src = newDir("s4src");
        Directory dir = newDir("s4")) {
      IndexWriter ws = new IndexWriter(src, config());
      ws.addDocument(contextSuggest("s1", "banana"));
      ws.close();
      IndexWriter w = new IndexWriter(dir, config());
      w.addDocument(suggest("1", "apple"));
      w.commit();
      Throwable thrown = null;
      try {
        w.addIndexes(src);
      } catch (Throwable t) {
        thrown = t;
      }
      step(sc, "addIndexes(Directory)", thrown);
      checkOpen(sc, w, "addIndexes(Directory)");
      if (w.isOpen()) {
        w.addDocument(plain("3"));
        thrown = null;
        try {
          w.forceMerge(1);
        } catch (Throwable t) {
          thrown = t;
        }
        step(sc, "addDocument(plain 3) + forceMerge(1)", thrown);
        checkOpen(sc, w, "forceMerge(1) after addIndexes(Directory)");
      }
      closeQuietly(sc, w);
      checkIds(sc, dir, "1", "3");
    }
  }

  public static void main(String[] args) throws Exception {
    control = args.length > 0 && "control".equals(args[0]);
    root = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "cmix");
    System.out.println(
        "lucene="
            + org.apache.lucene.util.Version.LATEST
            + " codec="
            + Codec.getDefault().getName()
            + " completionFormat="
            + COMPLETION.getName()
            + " mode="
            + (control ? "control (SuggestField only)" : "mixed"));
    twoDocumentsInOneSegment();
    oneDocumentWithBothTypes();
    twoCleanSegmentsPoisonEveryMerge();
    addIndexesDirectoryThenMerge();
    if (problems.isEmpty()) {
      System.out.println("RESULT: no scenario closed the writer or lost a document");
      System.exit(0);
    }
    System.out.println("RESULT: " + problems.size() + " contract violations");
    for (String p : problems) {
      System.out.println("  VIOLATION " + p);
    }
    System.exit(1);
  }
}
