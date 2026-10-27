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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/** Release-portable (core API only) variant: exits 1 if a rejected document closes the writer. */
public class CustomTermFreqRejectionClosesWriterMain {

  static final class TwoTokens extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAtt = addAttribute(TermFrequencyAttribute.class);
    private int upto;

    @Override
    public boolean incrementToken() {
      if (upto == 2) {
        return false;
      }
      clearAttributes();
      termAtt.append(upto == 0 ? "foo" : "bar");
      freqAtt.setTermFrequency(upto == 0 ? 1 : 2);
      upto++;
      return true;
    }

    @Override
    public void reset() throws java.io.IOException {
      super.reset();
      upto = 0;
    }
  }

  static Document idDoc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    return doc;
  }

  static String run(String name, FieldType ft) throws Exception {
    Path p = Files.createTempDirectory("h66-" + name);
    try (Directory dir = FSDirectory.open(p)) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      w.addDocument(idDoc("committed"));
      w.commit();
      w.addDocument(idDoc("uncommitted"));
      Document bad = idDoc("bad");
      bad.add(new Field("body", new TwoTokens(), ft));
      String rejected = "none";
      try {
        w.addDocument(bad);
      } catch (RuntimeException e) {
        rejected = e.getClass().getSimpleName();
      }
      Throwable tragic = w.getTragicException();
      String next;
      try {
        w.addDocument(idDoc("after"));
        w.commit();
        next = "ok";
      } catch (AlreadyClosedException e) {
        next = "AlreadyClosedException";
      }
      int committed;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        committed = r.numDocs();
      }
      w.close();
      return name + ": rejected=" + rejected + " tragic=" + tragic + " next=" + next
          + " committedDocs=" + committed;
    }
  }

  public static void main(String[] args) throws Exception {
    FieldType tvPos = new FieldType(TextField.TYPE_NOT_STORED);
    tvPos.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    tvPos.setStoreTermVectors(true);
    tvPos.setStoreTermVectorPositions(true);
    FieldType tvOff = new FieldType(TextField.TYPE_NOT_STORED);
    tvOff.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    tvOff.setStoreTermVectors(true);
    tvOff.setStoreTermVectorOffsets(true);
    FieldType postingsPos = new FieldType(TextField.TYPE_NOT_STORED);
    boolean bad = false;
    for (Object[] c :
        new Object[][] {{"tv-positions", tvPos}, {"tv-offsets", tvOff}, {"postings-positions", postingsPos}}) {
      String line = run((String) c[0], (FieldType) c[1]);
      System.out.println(line);
      if (!line.endsWith("tragic=null next=ok committedDocs=3".replace("tragic", "tragic"))
          && !line.contains("tragic=null next=ok committedDocs=3")) {
        bad = true;
      }
    }
    System.out.println(bad ? "BUG: a rejected document closed the IndexWriter" : "OK");
    System.exit(bad ? 1 : 0);
  }
}
