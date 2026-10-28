package org.apache.lucene.search.join;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Query-time join from an "orders" index to a "customers" index. One order has no customer key
 * (an optional foreign key). That order matches the from query but has no value in the from
 * field, so it must not relate to any customer. Only public API is used.
 */
public class TestJoinFromDocWithoutKeyV1 extends LuceneTestCase {

  private static final Query ALL_ORDERS = new TermQuery(new Term("kind", "order"));

  /** Names of the hits of {@code q}, with their scores, on a fresh searcher without query cache. */
  private static Map<String, Float> run(DirectoryReader reader, Query q) throws IOException {
    IndexSearcher s = new IndexSearcher(reader);
    s.setQueryCache(null);
    TopDocs td = s.search(q, 100);
    StoredFields sf = s.storedFields();
    Map<String, Float> out = new TreeMap<>();
    for (ScoreDoc sd : td.scoreDocs) {
      out.put(sf.document(sd.doc).get("name"), sd.score);
    }
    return out;
  }

  private static void addCustomer(IndexWriter w, String name, Field key) throws IOException {
    Document d = new Document();
    d.add(new StoredField("name", name));
    d.add(key);
    w.addDocument(d);
  }

  public void testTermJoinOrderWithoutCustomer() throws Exception {
    try (Directory toDir = newDirectory();
        Directory fromDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(toDir, new IndexWriterConfig())) {
        addCustomer(w, "emptyKeyCustomer", new StringField("id", "", Field.Store.NO));
        addCustomer(w, "alice", new StringField("id", "a1", Field.Store.NO));
        addCustomer(w, "bob", new StringField("id", "b2", Field.Store.NO));
      }
      try (IndexWriter w = new IndexWriter(fromDir, new IndexWriterConfig())) {
        Document d = new Document();
        d.add(new StringField("kind", "order", Field.Store.NO));
        d.add(new SortedDocValuesField("customer", new BytesRef("a1")));
        w.addDocument(d);
        d = new Document(); // order without a customer key
        d.add(new StringField("kind", "order", Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader to = DirectoryReader.open(toDir);
          DirectoryReader from = DirectoryReader.open(fromDir)) {
        IndexSearcher fromSearcher = new IndexSearcher(from);
        List<String> wrong = new ArrayList<>();
        for (ScoreMode sm : ScoreMode.values()) {
          for (boolean mv : new boolean[] {true, false}) {
            Query join =
                JoinUtil.createJoinQuery("customer", mv, "id", ALL_ORDERS, fromSearcher, sm);
            TreeSet<String> got = new TreeSet<>(run(to, join).keySet());
            String line = "term join mv=" + mv + " scoreMode=" + sm + " -> " + got;
            System.out.println(line);
            if (!got.equals(new TreeSet<>(List.of("alice")))) {
              wrong.add(line);
            }
          }
        }
        assertTrue("expected only [alice]; wrong: " + wrong, wrong.isEmpty());
      }
    }
  }

  public void testNumericJoinOrderWithoutCustomer() throws Exception {
    List<String> wrong = new ArrayList<>();
    for (Class<? extends Number> type : List.of(Integer.class, Long.class)) {
      try (Directory toDir = newDirectory();
          Directory fromDir = newDirectory()) {
        try (IndexWriter w = new IndexWriter(toDir, new IndexWriterConfig())) {
          for (int id : new int[] {0, 7, 9}) {
            Field key = type == Integer.class ? new IntPoint("id", id) : new LongPoint("id", id);
            addCustomer(w, "customer" + id, key);
          }
        }
        try (IndexWriter w = new IndexWriter(fromDir, new IndexWriterConfig())) {
          Document d = new Document();
          d.add(new StringField("kind", "order", Field.Store.NO));
          d.add(new NumericDocValuesField("customer", 7));
          w.addDocument(d);
          d = new Document(); // order without a customer key
          d.add(new StringField("kind", "order", Field.Store.NO));
          w.addDocument(d);
        }
        try (DirectoryReader to = DirectoryReader.open(toDir);
            DirectoryReader from = DirectoryReader.open(fromDir)) {
          IndexSearcher fromSearcher = new IndexSearcher(from);
          for (ScoreMode sm : ScoreMode.values()) {
            for (boolean mv : new boolean[] {true, false}) {
              Query join =
                  JoinUtil.createJoinQuery(
                      "customer", mv, "id", type, ALL_ORDERS, fromSearcher, sm);
              TreeSet<String> got = new TreeSet<>(run(to, join).keySet());
              String line =
                  "numeric(" + type.getSimpleName() + ") join mv=" + mv + " scoreMode=" + sm
                      + " -> " + got;
              System.out.println(line);
              if (!got.equals(new TreeSet<>(List.of("customer7")))) {
                wrong.add(line);
              }
            }
          }
        }
      }
    }
    assertTrue("expected only [customer7]; wrong: " + wrong, wrong.isEmpty());
  }

  /**
   * Customer 0 has exactly one order (score 1). A second order (score 5) has no customer. The join
   * score of customer 0 must come from its own order only.
   */
  public void testNumericJoinScoreOfCustomerZero() throws Exception {
    try (Directory toDir = newDirectory();
        Directory fromDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(toDir, new IndexWriterConfig())) {
        addCustomer(w, "customer0", new IntPoint("id", 0));
      }
      try (IndexWriter w = new IndexWriter(fromDir, new IndexWriterConfig())) {
        Document d = new Document();
        d.add(new StringField("tag", "cheap", Field.Store.NO));
        d.add(new NumericDocValuesField("customer", 0));
        w.addDocument(d);
        d = new Document(); // order without a customer key
        d.add(new StringField("tag", "pricey", Field.Store.NO));
        w.addDocument(d);
      }
      Query fromQuery =
          new BooleanQuery.Builder()
              .add(
                  new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("tag", "cheap"))), 1f),
                  BooleanClause.Occur.SHOULD)
              .add(
                  new BoostQuery(
                      new ConstantScoreQuery(new TermQuery(new Term("tag", "pricey"))), 5f),
                  BooleanClause.Occur.SHOULD)
              .build();
      try (DirectoryReader to = DirectoryReader.open(toDir);
          DirectoryReader from = DirectoryReader.open(fromDir)) {
        IndexSearcher fromSearcher = new IndexSearcher(from);
        List<String> wrong = new ArrayList<>();
        for (ScoreMode sm : new ScoreMode[] {ScoreMode.Max, ScoreMode.Avg, ScoreMode.Total}) {
          for (boolean mv : new boolean[] {true, false}) {
            Query join =
                JoinUtil.createJoinQuery(
                    "customer", mv, "id", Integer.class, fromQuery, fromSearcher, sm);
            Map<String, Float> got = run(to, join);
            String line = "numeric score mv=" + mv + " scoreMode=" + sm + " -> " + got;
            System.out.println(line);
            if (!got.equals(Map.of("customer0", 1f))) {
              wrong.add(line);
            }
          }
        }
        assertTrue("expected {customer0=1.0}; wrong: " + wrong, wrong.isEmpty());
      }
    }
  }
}
