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
package dshist;

import java.util.Arrays;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.classification.utils.DatasetSplitter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;

/**
 * Version-portable main() repro (API era eraF-10.x-main): DatasetSplitter.split with explicit fieldNames
 * copies only the first value of a multi-valued stored field.
 *
 * <p>Source index: 3 documents, each with the stored fields id (StringField), text (TextField with
 * two values, "first value" and "second value") and cls (StringField, label "a", plus sorted doc values).
 * DatasetSplitter(0.3, 0.3) puts one document each into the test, cross-validation and training
 * index. The split runs twice: fieldNames = null (control: all fields) and fieldNames = {id, text,
 * cls} (every stored field named explicitly). Oracle: each copied document keeps both stored values
 * of text, and the term "second" is indexed for each copied document (docFreq sums to 3).
 *
 * <p>Exit 1 = bug reproduced, 0 = not reproduced, 3 = control broken (inconclusive).
 */
public class DatasetSplitterHistoryMain {

  static final String[] NAMED = {"id", "text", "cls"};

  static Analyzer analyzer() {
    return new WhitespaceAnalyzer();
  }

  static IndexWriterConfig iwc(Analyzer a) {
    return new IndexWriterConfig(a);
  }

  static Directory newDir() {
    return new org.apache.lucene.store.ByteBuffersDirectory();
  }

  static Document storedDoc(IndexReader r, int docId) throws Exception {
    return r.storedFields().document(docId);
  }

  static Directory buildSource(Analyzer a) throws Exception {
    Directory src = newDir();
    IndexWriter w = new IndexWriter(src, iwc(a));
    for (int i = 0; i < 3; i++) {
      Document d = new Document();
      d.add(new StringField("id", "d" + i, Field.Store.YES));
      d.add(new TextField("text", "first value", Field.Store.YES));
      d.add(new TextField("text", "second value", Field.Store.YES));
      d.add(new StringField("cls", "a", Field.Store.YES));
      d.add(new org.apache.lucene.document.SortedDocValuesField("cls", new org.apache.lucene.util.BytesRef("a")));
      w.addDocument(d);
    }
    w.close();
    return src;
  }

  /** Returns {docs keeping both text values, docs copied, docFreq(text:second) summed over the 3 indexes}. */
  static int[] runSplit(String label, String[] fieldNames, StringBuilder out) throws Exception {
    Analyzer a = analyzer();
    Directory src = buildSource(a);
    Directory train = newDir();
    Directory test = newDir();
    Directory cv = newDir();
    DirectoryReader reader = DirectoryReader.open(src);
    if (reader.leaves().size() != 1) {
      throw new IllegalStateException("expected one segment, got " + reader.leaves().size());
    }
    new DatasetSplitter(0.3, 0.3).split(reader, train, test, cv, a, false, "cls", fieldNames);
    try {
      reader.close();
    } catch (Exception alreadyClosedBySplit) {
      // some versions close the reader (or a wrapper of it) inside split
    }
    int both = 0, copied = 0, dfSecond = 0;
    out.append(label).append(':');
    Directory[] dirs = {train, test, cv};
    String[] names = {"train", "test", "cv"};
    for (int k = 0; k < 3; k++) {
      DirectoryReader r = DirectoryReader.open(dirs[k]);
      int ds = r.docFreq(new Term("text", "second"));
      dfSecond += ds;
      out.append(' ').append(names[k]).append("={");
      for (int i = 0; i < r.maxDoc(); i++) {
        Document d = storedDoc(r, i);
        String[] vals = d.getValues("text");
        copied++;
        if (vals.length == 2) {
          both++;
        }
        out.append(i > 0 ? "; " : "").append(d.get("id")).append(" text=").append(Arrays.toString(vals));
      }
      out.append("} df(text:second)=").append(ds);
      r.close();
    }
    out.append('\n');
    return new int[] {both, copied, dfSecond};
  }

  public static void main(String[] args) throws Exception {
    StringBuilder sb = new StringBuilder();
    int[] control = runSplit("control fieldNames=null", null, sb);
    int[] named = runSplit("named fieldNames={id, text, cls}", NAMED, sb);
    System.out.print(sb);
    System.out.println(
        "SUMMARY control: "
            + control[0] + "/" + control[1] + " docs keep both text values, df(text:second)=" + control[2]
            + "; named: "
            + named[0] + "/" + named[1] + " docs keep both text values, df(text:second)=" + named[2]);
    if (control[1] != 3 || control[0] != 3 || control[2] != 3) {
      System.out.println("VERDICT: CONTROL BROKEN (inconclusive)");
      System.exit(3);
    }
    if (named[1] == 3 && named[0] == 3 && named[2] == 3) {
      System.out.println("VERDICT: not reproduced");
      return;
    }
    System.out.println("VERDICT: BUG REPRODUCED");
    System.exit(1);
  }
}
