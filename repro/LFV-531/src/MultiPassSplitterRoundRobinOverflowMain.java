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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.misc.index.MultiPassIndexSplitter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Round-robin MultiPassIndexSplitter on an index with IndexWriter.MAX_DOCS documents: every document
 * must land in exactly one part, so the parts' doc counts must add up to the input's.
 *
 * <p>args: [numParts (default 145)] [maxDoc (default IndexWriter.MAX_DOCS)] [work dir (default a new
 * temp dir)]. Exit 1 if the parts do not add up.
 */
public class MultiPassSplitterRoundRobinOverflowMain {
  public static void main(String[] args) throws Exception {
    int numParts = args.length > 0 ? Integer.parseInt(args[0]) : 145;
    int maxDoc = args.length > 1 ? Integer.parseInt(args[1]) : IndexWriter.MAX_DOCS;
    Path work = args.length > 2 ? Files.createDirectories(Path.of(args[2])) : Files.createTempDirectory("mpis");
    long t0 = System.nanoTime();
    Path src = work.resolve("src");
    try (Directory dir = FSDirectory.open(src);
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setRAMBufferSizeMB(1024))) {
      // empty documents: the split only moves document slots, so content is not needed
      List<Document> batch = Collections.nCopies(100_000, new Document());
      long added = 0;
      while (added + batch.size() <= maxDoc) {
        w.addDocuments(batch);
        added += batch.size();
      }
      w.addDocuments(Collections.nCopies((int) (maxDoc - added), new Document()));
      w.commit();
    }
    System.out.printf("indexed %d empty docs in %.0f s%n", maxDoc, (System.nanoTime() - t0) / 1e9);
    List<Directory> outs = new ArrayList<>();
    try (Directory dir = FSDirectory.open(src);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      System.out.println("input maxDoc=" + reader.maxDoc() + " numDocs=" + reader.numDocs() + " leaves=" + reader.leaves().size());
      Directory[] parts = new Directory[numParts];
      for (int i = 0; i < numParts; i++) {
        parts[i] = FSDirectory.open(work.resolve("part-" + i));
        outs.add(parts[i]);
      }
      long t1 = System.nanoTime();
      new MultiPassIndexSplitter().split(reader, parts, false); // round-robin
      System.out.printf("split into %d parts in %.0f s%n", numParts, (System.nanoTime() - t1) / 1e9);
      long total = 0;
      StringBuilder odd = new StringBuilder();
      for (int i = 0; i < numParts; i++) {
        try (DirectoryReader r = DirectoryReader.open(parts[i])) {
          total += r.numDocs();
          // docs j with j % numParts == i (long arithmetic: maxDoc + numParts exceeds an int here)
          long expected = ((long) reader.maxDoc() - i + numParts - 1) / numParts;
          if (r.numDocs() != expected) {
            odd.append(" part ").append(i).append(": ").append(r.numDocs()).append(" (expected ").append(expected).append(')');
          }
        }
      }
      System.out.println("sum of parts' numDocs=" + total + " input numDocs=" + reader.numDocs());
      System.out.println("parts with an unexpected count:" + (odd.length() == 0 ? " none" : odd));
      for (Directory d : outs) d.close();
      if (total != reader.numDocs() || odd.length() != 0) {
        System.out.println("FAIL: documents lost or duplicated by the round-robin split");
        System.exit(1);
      }
      System.out.println("PASS");
    }
  }
}
