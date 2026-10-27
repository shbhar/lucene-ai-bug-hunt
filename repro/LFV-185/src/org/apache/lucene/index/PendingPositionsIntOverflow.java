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
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * Two adjacent documents hold n0 = 2^30 + 1 and n1 = 2^30 + 2 occurrences of the term "t" (all
 * occurrences of a document at one position: 0 in doc 0, 1 in doc 1). Skip doc 0's positions, then
 * read doc 1's first position: it must be 1. Uses only lucene-core. Exit 0 = correct, 1 = wrong.
 */
public final class PendingPositionsIntOverflow {

  /** {@code lead} filler tokens "y", then {@code n} tokens "t" all at the same position. */
  static final class RepeatedToken extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posInc = addAttribute(PositionIncrementAttribute.class);
    private final int lead;
    private final int n;
    private int i;

    RepeatedToken(int lead, int n) {
      this.lead = lead;
      this.n = n;
    }

    @Override
    public boolean incrementToken() {
      if (i >= lead + n) {
        return false;
      }
      clearAttributes();
      term.append(i < lead ? "y" : "t");
      posInc.setPositionIncrement(i <= lead ? 1 : 0);
      i++;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      i = 0;
    }
  }

  public static void main(String[] args) throws Exception {
    final int n0 = (1 << 30) + 1;
    final int n1 = (1 << 30) + 2; // n0 + n1 > Integer.MAX_VALUE
    Path path = Files.createTempDirectory("pending-positions");
    try (Directory dir = FSDirectory.open(path)) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setRAMPerThreadHardLimitMB(2047);
      iwc.setRAMBufferSizeMB(2000);
      iwc.setMergePolicy(new LogDocMergePolicy()); // merges keep segment (and doc) order
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document d0 = new Document();
        d0.add(new TextField("f", new RepeatedToken(0, n0))); // all "t" at position 0
        w.addDocument(d0);
        w.flush();
        Document d1 = new Document();
        d1.add(new TextField("f", new RepeatedToken(1, n1))); // "y" at 0, all "t" at position 1
        w.addDocument(d1);
        w.flush();
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        check(reader.leaves().size() == 1, "one segment");
        System.out.println("codec=" + reader.leaves().get(0).reader().getClass().getSimpleName()
            + " " + org.apache.lucene.util.Version.LATEST);
        TermsEnum te = reader.leaves().get(0).reader().terms("f").iterator();
        check(te.seekExact(new BytesRef("t")), "term t");
        PostingsEnum pe = te.postings(null, PostingsEnum.POSITIONS);
        check(pe.nextDoc() == 0, "doc 0");
        check(pe.freq() == n0, "freq of doc 0 = " + pe.freq());
        // do not read doc 0's positions
        check(pe.nextDoc() == 1, "doc 1");
        check(pe.freq() == n1, "freq of doc 1 = " + pe.freq());
        int p = pe.nextPosition();
        System.out.println("first position of t in doc 1: " + p + " (expected 1)");
        check(p == 1, "first position of doc 1 is " + p + ", expected 1");
      }
    } finally {
      IOUtils.rm(path);
    }
    System.out.println("OK");
  }

  static void check(boolean ok, String what) {
    if (!ok) {
      System.out.println("FAIL: " + what);
      System.exit(1);
    }
  }
}
