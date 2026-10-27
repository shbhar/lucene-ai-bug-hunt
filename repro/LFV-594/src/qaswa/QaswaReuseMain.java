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
package qaswa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.query.QueryAutoStopWordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;

/**
 * Release-sweep variant (Lucene 5.0 and later, no test framework): exits 1 when
 * QueryAutoStopWordAnalyzer applies one field's stop words to another field.
 */
public class QaswaReuseMain {

  static List<String> terms(Analyzer a, String field, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream(field, text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(t.toString());
      ts.end();
    }
    return out;
  }

  static Directory newDir() throws Exception {
    Class<?> c;
    try {
      c = Class.forName("org.apache.lucene.store.ByteBuffersDirectory");
    } catch (ClassNotFoundException e) {
      c = Class.forName("org.apache.lucene.store.RAMDirectory");
    }
    return (Directory) c.getConstructor().newInstance();
  }

  public static void main(String[] args) throws Exception {
    Analyzer delegate = new StandardAnalyzer();
    Directory dir = newDir();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(delegate));
    for (int i = 0; i < 20; i++) {
      Document doc = new Document();
      doc.add(new TextField("repetitiveField", "boring", Field.Store.NO));
      doc.add(new TextField("variedField", i == 0 ? "boring" : "word" + i, Field.Store.NO));
      w.addDocument(doc);
    }
    w.close();
    DirectoryReader reader = DirectoryReader.open(dir);
    QueryAutoStopWordAnalyzer a =
        new QueryAutoStopWordAnalyzer(delegate, reader, Arrays.asList("repetitiveField"), 10);
    List<String> rep = terms(a, "repetitiveField", "boring");
    List<String> var = terms(a, "variedField", "boring");
    System.out.println("stop words repetitiveField=" + Arrays.toString(a.getStopWords("repetitiveField"))
        + " variedField=" + Arrays.toString(a.getStopWords("variedField")));
    System.out.println("repetitiveField 'boring' -> " + rep + " (expected [])");
    System.out.println("variedField 'boring' -> " + var + " (expected [boring])");
    a.close();
    reader.close();
    dir.close();
    boolean ok = rep.isEmpty() && var.equals(Arrays.asList("boring"));
    System.out.println(ok ? "OK" : "BUG: stop words of repetitiveField were applied to variedField");
    System.exit(ok ? 0 : 1);
  }
}
