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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Cross-field BlendedTermQuery (the use case its class javadoc describes) gives every term the
 * maximum docFreq over all its terms. When one of the fields is present in fewer documents than
 * that maximum, the term's statistics leave the bounds of its field's statistics (TermStats
 * javadoc: totalTermFreq never exceeds the field's sumTotalTermFreq), BM25's idf turns negative
 * and so does the score (MIGRATE.md, LUCENE-7996: scorers must not produce negative scores). Top-k
 * search then silently drops the matching document. When the field is absent from the index, the
 * search throws NullPointerException instead.
 */
public class TestBlendedTermQueryCrossFieldStats extends LuceneTestCase {

  private static Document doc(String id, String title, String keywords) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (title != null) {
      d.add(new TextField("title", title, Field.Store.NO));
    }
    if (keywords != null) {
      d.add(new TextField("keywords", keywords, Field.Store.NO));
    }
    return d;
  }

  private static BlendedTermQuery blended(BlendedTermQuery.RewriteMethod rewrite, String... fields) {
    BlendedTermQuery.Builder b = new BlendedTermQuery.Builder().setRewriteMethod(rewrite);
    for (String f : fields) {
      b.add(new Term(f, "lucene"));
    }
    return b.build();
  }

  /** A sparse field: "keywords" exists in one document, "lucene" is in two titles. */
  public void testSparseFieldOneSegment() throws Exception {
    checkSparseField(false);
  }

  /** Same documents, but doc 2 sits in a segment of its own. */
  public void testSparseFieldThreeSegments() throws Exception {
    checkSparseField(true);
  }

  private void checkSparseField(boolean multiSegment) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("0", "lucene in action", null));
        w.addDocument(doc("1", "lucene", null));
        if (multiSegment) {
          w.flush();
        }
        w.addDocument(doc("2", "search engines", "lucene"));
        if (multiSegment) {
          w.flush();
        }
        w.addDocument(doc("3", "other things", null));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        StringBuilder problems = new StringBuilder();
        for (BlendedTermQuery.RewriteMethod rewrite :
            new BlendedTermQuery.RewriteMethod[] {
              BlendedTermQuery.DISJUNCTION_MAX_REWRITE, BlendedTermQuery.BOOLEAN_REWRITE
            }) {
          Query q = blended(rewrite, "title", "keywords");
          assertEquals(q.toString(), 3, searcher.count(q));
          // doc 2 matches (keywords:lucene only) and explain() agrees that it matches
          Explanation expl = searcher.explain(q, 2);
          assertTrue(expl.toString(), expl.isMatch());
          TopDocs top = searcher.search(q, 10);
          StringBuilder hits = new StringBuilder();
          for (ScoreDoc sd : top.scoreDocs) {
            hits.append(searcher.storedFields().document(sd.doc).get("id"))
                .append(':')
                .append(sd.score)
                .append(' ');
          }
          String rw = rewrite == BlendedTermQuery.BOOLEAN_REWRITE ? "BOOLEAN_REWRITE" : "DISJUNCTION_MAX_REWRITE";
          if (top.scoreDocs.length != 3) {
            problems.append(rw + ": search(q, 10) returned " + top.scoreDocs.length
                + " of the 3 matching documents [" + hits + "]\n");
          }
          if (expl.getValue().floatValue() < 0f) {
            problems.append(rw + ": doc 2 scores " + expl.getValue() + " (negative), hits [" + hits + "]\n");
          }
        }
        assertTrue("\n" + problems, problems.length() == 0);
      }
    }
  }

  /** A field that is in the schema but not (yet) in any indexed document. */
  public void testFieldAbsentFromIndexThrows() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc("0", "lucene in action", null));
        w.addDocument(doc("1", "search engines", null));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q = blended(BlendedTermQuery.DISJUNCTION_MAX_REWRITE, "title", "keywords");
        assertEquals(1, searcher.count(q));
        // throws NullPointerException in BM25Similarity.idfExplain on main
        TopDocs top = searcher.search(q, 10);
        assertEquals(1, top.scoreDocs.length);
      }
    }
  }
}
