package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/** Print-only probe for the doc's steps (not the repro): search order and index-sort reopen. */
public class StepsProbe {

  public static void main(String[] args) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc("apple"));
        w.addDocument(doc("banana"));
        w.addDocument(doc(null));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        for (Object mv : new Object[] {"zzz", SortField.STRING_FIRST, SortField.STRING_LAST}) {
          SortField sf = KeywordField.newSortField("tag", false, SortedSetSelector.Type.MIN, mv);
          System.out.println(
              "search missing=" + mv + " sortField=" + sf + " getMissingValue=" + sf.getMissingValue()
                  + " order=" + order(s, sf));
        }
        SortField sv = new SortField("tag", SortField.Type.STRING_VAL, false, "zzz");
        System.out.println("STRING_VAL accepted: " + sv);
      }
    }
    // Within one writer session: two flushed segments merged into one, with the "zzz" index sort.
    Sort zzz = new Sort(KeywordField.newSortField("tag", false, SortedSetSelector.Type.MIN, "zzz"));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(zzz))) {
        w.addDocument(doc("banana"));
        w.commit();
        w.addDocument(doc(null));
        w.addDocument(doc("apple"));
        w.commit();
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.maxDoc(); i++) {
          if (sb.length() > 0) sb.append(", ");
          sb.append(r.storedFields().document(i).get("name"));
        }
        System.out.println(
            "same session, 2 commits + forceMerge(1): OK, segments=" + r.leaves().size()
                + " stored order=" + sb);
      }
    }
    for (Object mv : new Object[] {"zzz", SortField.STRING_LAST}) {
      Sort sort = new Sort(KeywordField.newSortField("tag", false, SortedSetSelector.Type.MIN, mv));
      try (Directory dir = new ByteBuffersDirectory()) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(sort))) {
          w.addDocument(doc("apple"));
          w.addDocument(doc(null));
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          System.out.println(
              "indexSort missing=" + mv + " config=" + sort + " segment="
                  + r.leaves().get(0).reader().getMetaData().sort());
        }
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(sort))) {
          System.out.println("reopen missing=" + mv + ": OK");
        } catch (IllegalArgumentException e) {
          System.out.println("reopen missing=" + mv + ": IllegalArgumentException: " + e.getMessage());
          Sort plain = new Sort(KeywordField.newSortField("tag", false, SortedSetSelector.Type.MIN));
          try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(plain))) {
            System.out.println("reopen with the value removed (" + plain + "): OK");
          }
        }
      }
    }
  }

  private static Document doc(String tag) {
    Document d = new Document();
    d.add(new StoredField("name", tag == null ? "(none)" : tag));
    if (tag != null) {
      d.add(new KeywordField("tag", tag, Field.Store.NO));
    }
    return d;
  }

  private static String order(IndexSearcher s, SortField sf) throws Exception {
    TopDocs td = s.search(MatchAllDocsQuery.INSTANCE, 10, new Sort(sf));
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(s.storedFields().document(sd.doc).get("name"));
    }
    return sb.toString();
  }
}
