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

import java.io.StringReader;
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
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

/**
 * Release-sweep variant for Lucene 4.x (Version-taking constructors, Reader-based tokenStream): exits 1 when
 * QueryAutoStopWordAnalyzer applies one field's stop words to another field.
 */
public class QaswaReuseMain4x {

  static List<String> terms(Analyzer a, String field, String text) throws Exception {
    List<String> out = new ArrayList<>();
    TokenStream ts = a.tokenStream(field, new StringReader(text));
    {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(t.toString());
      ts.end();
      ts.close();
    }
    return out;
  }

  public static void main(String[] args) throws Exception {
    Analyzer delegate = new StandardAnalyzer(Version.LUCENE_CURRENT);
    Directory dir = new RAMDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(Version.LUCENE_CURRENT, delegate));
    for (int i = 0; i < 20; i++) {
      Document doc = new Document();
      doc.add(new TextField("repetitiveField", "boring", Field.Store.NO));
      doc.add(new TextField("variedField", i == 0 ? "boring" : "word" + i, Field.Store.NO));
      w.addDocument(doc);
    }
    w.close();
    DirectoryReader reader = DirectoryReader.open(dir);
    QueryAutoStopWordAnalyzer a =
        new QueryAutoStopWordAnalyzer(Version.LUCENE_CURRENT, delegate, reader, Arrays.asList("repetitiveField"), 10);
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
