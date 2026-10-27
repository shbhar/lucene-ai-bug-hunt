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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

/**
 * The short visual's example (2026-10-24, local evaluation only), as plain Java on lucene-core
 * only: no test framework, no randomness, default IndexWriterConfig (default codec, compound files
 * on). Writer 1 adds id="old" plus a stored field "body" of N pseudo-random letters, opens an NRT
 * reader R and rolls back; writer 2 opens dir and rolls back; writer 3 adds id="new" plus N letters
 * of its own (same length) and closes; then T's writer copies R's only segment with addIndexes.
 * The package is org.apache.lucene.index only to print the stale reader's inner .fdt length.
 *
 * <p>Variants: abc-body4096 (ADVISE_BY_CONTEXT, body 4096 letters), default-body4096 (default
 * advice), abc-idonly (ADVISE_BY_CONTEXT, no body: the old hero), abc-body3000; a "-flush" suffix
 * makes writer 3 only flush (no commit) and stay open during the copy.
 */
public class VisualExample {

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    String[] variants =
        args.length > 1
            ? Arrays.copyOfRange(args, 1, args.length)
            : new String[] {
              "abc-body4096", "default-body4096", "abc-idonly", "abc-body3000", "abc-body4096-flush"
            };
    System.out.println(
        "VISUAL env lucene="
            + Version.LATEST
            + " java="
            + Runtime.version()
            + " os="
            + System.getProperty("os.name")
            + "/"
            + System.getProperty("os.arch")
            + " nativeAccess="
            + VisualExample.class.getModule().isNativeAccessEnabled());
    for (String variant : variants) {
      run(root.resolve(variant), variant);
    }
  }

  private static void run(Path path, String variant) throws Exception {
    boolean adviseByContext = variant.startsWith("abc");
    boolean flushOnly = variant.endsWith("-flush"); // writer 3 only flushes, commits nothing
    String size = flushOnly ? variant.substring(0, variant.length() - "-flush".length()) : variant;
    int bodyLength = size.endsWith("body4096") ? 4096 : size.endsWith("body3000") ? 3000 : 0;
    Files.createDirectories(path);
    try (MMapDirectory dir = new MMapDirectory(path);
        Directory target = new ByteBuffersDirectory()) {
      if (adviseByContext) {
        dir.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT); // the one non-default setting
      }
      IndexWriter w1 = new IndexWriter(dir, new IndexWriterConfig());
      w1.addDocument(doc("old", bodyLength, 0));
      DirectoryReader reader = DirectoryReader.open(w1); // flushes _0, commits nothing
      w1.rollback(); // R stays open
      new IndexWriter(dir, new IndexWriterConfig()).rollback(); // deletes _0's files
      IndexWriter w3 = new IndexWriter(dir, new IndexWriterConfig());
      w3.addDocument(doc("new", bodyLength, 1)); // a different segment, also named _0
      if (flushOnly) {
        w3.flush(); // writes writer 3's _0.cfs, commits nothing; w3 stays open
      } else {
        w3.close(); // also commits
      }
      SegmentReader leaf = (SegmentReader) reader.leaves().get(0).reader();
      String latestText;
      if (flushOnly) {
        latestText = "flush only, files in dir: " + Arrays.toString(dir.listAll());
      } else {
        SegmentCommitInfo latest = SegmentInfos.readLatestCommit(dir).info(0);
        latestText =
            "latest commit: segment="
                + latest.info.name
                + " id="
                + StringHelper.idToString(latest.info.getId());
      }
      String names =
          "R: segment="
              + leaf.getSegmentName()
              + " id="
              + StringHelper.idToString(leaf.getSegmentInfo().info.getId())
              + " cfs="
              + leaf.getSegmentInfo().info.getUseCompoundFile()
              + " | "
              + latestText;
      String sizes = sizes(leaf, dir);
      String thrown = null;
      try (IndexWriter t = new IndexWriter(target, new IndexWriterConfig())) {
        t.addIndexes(leaf); // copy R's only segment into T
      } catch (Throwable e) {
        thrown = e.toString();
      }
      String own = fingerprint(reader.storedFields().document(0));
      reader.close();
      if (flushOnly) {
        w3.rollback();
      }
      String copied = "-";
      String check = "-";
      if (thrown == null) {
        try (DirectoryReader copy = DirectoryReader.open(target)) {
          copied = fingerprint(copy.storedFields().document(0)) + " maxDoc=" + copy.maxDoc();
        }
        try (CheckIndex checker = new CheckIndex(target)) {
          check = "clean=" + checker.checkIndex().clean;
        }
      }
      System.out.println(
          "VISUAL "
              + variant
              + " | "
              + names
              + " | "
              + sizes
              + " | R own after copy: "
              + own
              + " | T copy: "
              + copied
              + " | CheckIndex(T) "
              + check
              + " | threw="
              + thrown
              + " | verdict="
              + (thrown != null ? "THREW" : copied.startsWith(own) ? "RIGHT" : "WRONG"));
    }
  }

  private static Document doc(String id, int bodyLength, int shift) {
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    if (bodyLength > 0) {
      doc.add(new StoredField("body", letters(bodyLength, shift)));
    }
    return doc;
  }

  private static String fingerprint(Document d) {
    String body = d.get("body");
    return "id="
        + d.get("id")
        + (body == null ? "" : " body=" + body.substring(0, 12) + "...(" + body.length() + ")");
  }

  /** Lengths of the stored-fields data file as the stale reader sees it. */
  private static String sizes(SegmentReader leaf, Directory dir) throws Exception {
    boolean cfs = leaf.getSegmentInfo().info.getUseCompoundFile();
    Directory files = cfs ? leaf.core.cfsReader : dir;
    TreeMap<String, Long> lengths = new TreeMap<>();
    for (String file : files.listAll()) {
      if (file.startsWith(leaf.getSegmentName()) && file.endsWith(".fdt")) {
        lengths.put(file, files.fileLength(file));
      }
    }
    return (cfs ? "compound inner " : "on disk ") + lengths;
  }

  /** Letters from a fixed LCG (x0 = 42), each shifted by `shift`: as long once compressed. */
  private static String letters(int length, int shift) {
    StringBuilder value = new StringBuilder();
    long x = 42;
    while (value.length() < length) {
      x = x * 6364136223846793005L + 1442695040888963407L;
      value.append((char) ('a' + (int) ((x >>> 33) % 26 + shift) % 26));
    }
    return value.toString();
  }
}
