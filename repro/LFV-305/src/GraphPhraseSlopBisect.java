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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

/** Version-portable main(): exits 1 when "guinea pig food"~1 (with guinea pig -> cavy) misses a doc it must match. */
public class GraphPhraseSlopBisect {
  public static void main(String[] args) throws Exception {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    b.add(SynonymMap.Builder.join(new String[] {"guinea", "pig"}, new CharsRefBuilder()), new CharsRef("cavy"), true);
    final SynonymMap map = b.build();
    Analyzer syn =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new SynonymGraphFilter(t, map, true));
          }
        };
    Directory dir = FSDirectory.open(Files.createTempDirectory("slop"));
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()));
    Document d = new Document();
    d.add(new TextField("body", "my guinea pig dry food", Field.Store.NO));
    w.addDocument(d);
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    IndexSearcher s = new IndexSearcher(r);
    QueryParser qp = new QueryParser("body", syn);
    Query control = qp.parse("\"pig food\"~1");
    Query graph = qp.parse("\"guinea pig food\"~1");
    int c = s.count(control), g = s.count(graph);
    System.out.println("control " + control + " -> " + c);
    System.out.println("graph   " + graph + " -> " + g);
    r.close();
    dir.close();
    if (c != 1) throw new AssertionError("control phrase must match");
    if (g != 1) throw new AssertionError("explicit slop ~1 dropped for the graph phrase: " + graph);
  }
}
