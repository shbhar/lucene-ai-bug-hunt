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
package org.apache.lucene.facet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Main (9.0+, with the per-instance built-dim cache): does the symptom depend on whether the
 * original was evaluated before the clone was modified? Prints the counts of three scenarios.
 */
public class DrillDownCloneStateMain {
  public static void main(String[] args) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (String color : new String[] {"red", "blue", "green"}) {
      Document doc = new Document();
      doc.add(new StringField("color", color, Field.Store.NO));
      w.addDocument(doc);
    }
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    IndexSearcher s = new IndexSearcher(r);
    FacetsConfig config = new FacetsConfig();

    // A: original NOT evaluated before the clone is modified
    DrillDownQuery a = new DrillDownQuery(config);
    a.add("color", new TermQuery(new Term("color", "red")));
    DrillDownQuery ac = a.clone();
    ac.add("color", new TermQuery(new Term("color", "blue")));
    System.out.println("A clone=" + s.count(ac) + " original=" + s.count(a) + " equals=" + a.equals(ac));

    // B: original evaluated before the clone is modified
    DrillDownQuery b = new DrillDownQuery(config);
    b.add("color", new TermQuery(new Term("color", "red")));
    int bBefore = s.count(b);
    DrillDownQuery bc = b.clone();
    bc.add("color", new TermQuery(new Term("color", "blue")));
    System.out.println(
        "B before=" + bBefore + " clone=" + s.count(bc) + " original=" + s.count(b) + " equals=" + b.equals(bc));
    // B2: then the original ORs in its own value: the clone's value leaks in as well
    b.add("color", new TermQuery(new Term("color", "green")));
    System.out.println("B2 original after adding green=" + s.count(b) + " (red+green would be 2)");
    r.close();
    dir.close();
  }
}
