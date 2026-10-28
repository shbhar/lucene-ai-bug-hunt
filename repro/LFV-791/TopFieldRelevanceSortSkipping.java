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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * IndexSearcher#search(query, 10) and IndexSearcher#search(query, 10, Sort.RELEVANCE) both count
 * hits accurately up to 1000 and then may skip non-competitive hits (ScoreMode.TOP_SCORES). Here the
 * best documents come first (docs 0-1010 match a b c, docs 1011-10010 match only a b), so once 1000
 * hits are counted nothing after doc 1010 can enter the top 10. The score-sorted search skips them;
 * the relevance-sorted TopFieldCollector collects every hit, because the min competitive score is
 * only ever set on a competitive hit. Exits 1 when the relevance-sorted search counts more than
 * twice as many hits as the score-sorted one.
 */
public class TopFieldRelevanceSortSkipping {
  public static void main(String[] args) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
    for (int i = 0; i < 10011; i++) {
      Document d = new Document();
      d.add(new TextField("body", i <= 1010 ? "a b c" : "a b", Field.Store.NO));
      w.addDocument(d);
    }
    w.forceMerge(1);
    w.close();
    DirectoryReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : new String[] {"a", "b", "c"}) {
      b.add(new TermQuery(new Term("body", t)), BooleanClause.Occur.SHOULD);
    }
    Query q = b.build();
    TopDocs byScore = searcher.search(q, 10);
    TopDocs byRelevanceSort = searcher.search(q, 10, Sort.RELEVANCE);
    boolean sameTop = byScore.scoreDocs.length == byRelevanceSort.scoreDocs.length;
    for (int i = 0; sameTop && i < byScore.scoreDocs.length; i++) {
      sameTop = byScore.scoreDocs[i].doc == byRelevanceSort.scoreDocs[i].doc;
    }
    System.out.println("matches=" + searcher.count(q));
    System.out.println("search(q, 10):                totalHits=" + byScore.totalHits);
    System.out.println("search(q, 10, Sort.RELEVANCE): totalHits=" + byRelevanceSort.totalHits);
    System.out.println("same top 10 doc ids: " + sameTop);
    reader.close();
    dir.close();
    long a = value(byScore.totalHits), c = value(byRelevanceSort.totalHits);
    if (!sameTop) {
      System.out.println("UNEXPECTED: top hits differ");
      System.exit(2);
    }
    if (c > 2 * a) {
      System.out.println("FAIL: the relevance-sorted search collected " + c + " hits, the score-sorted one " + a);
      System.exit(1);
    }
    System.out.println("OK");
  }

  /** TotalHits.value is a field before 10.0 and a record accessor since; read either. */
  static long value(org.apache.lucene.search.TotalHits th) throws Exception {
    try {
      return (Long) th.getClass().getMethod("value").invoke(th);
    } catch (NoSuchMethodException e) {
      return th.getClass().getField("value").getLong(th);
    }
  }
}
