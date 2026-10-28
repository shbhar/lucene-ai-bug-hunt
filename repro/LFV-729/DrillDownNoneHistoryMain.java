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
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Version-independent history probe (public API only; compiles on 8.9.0 and later).
 *
 * <p>Every scenario uses FacetsConfig with setDrillDownTermsIndexing("rating", NONE) and leaves
 * every dimension in the default index field $facets. "author" keeps the default (ALL). Each
 * scenario indexes two documents and prints ACCEPTED (both indexed, numDocs == 2) or REJECTED with
 * the exception message.
 *
 * <ul>
 *   <li>T1 taxonomy FacetField: rating-only doc, then author doc
 *   <li>T2 taxonomy FacetField: author doc, then rating-only doc
 *   <li>S1 SortedSetDocValuesFacetField: rating-only doc, then author doc
 *   <li>S2 SortedSetDocValuesFacetField: author doc, then rating-only doc
 *   <li>P1 taxonomy: rating-only doc, commit, close, new writer (APPEND), author doc
 *   <li>C1 control: like T1 but rating has its own index field (the workaround)
 *   <li>C2 control: like T1 but no dimension uses NONE
 * </ul>
 *
 * <p>Exit status: 0 if every scenario is ACCEPTED (expected behaviour), 1 if any of T1, T2, S1,
 * S2, P1 is REJECTED (bug present) while both controls are ACCEPTED, 2 if a control is REJECTED.
 */
public class DrillDownNoneHistoryMain {

  private static FacetsConfig config(boolean noneDim, boolean ownField) {
    FacetsConfig config = new FacetsConfig();
    if (noneDim) {
      config.setDrillDownTermsIndexing("rating", FacetsConfig.DrillDownTermsIndexing.NONE);
    }
    if (ownField) {
      config.setIndexFieldName("rating", "rating_facets");
    }
    return config;
  }

  private static Document taxoDoc(String dim, String value) {
    Document doc = new Document();
    doc.add(new FacetField(dim, value));
    return doc;
  }

  private static Document ssdvDoc(String dim, String value) {
    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField(dim, value));
    return doc;
  }

  private static int numDocs(IndexWriter writer) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(writer)) {
      return reader.numDocs();
    }
  }

  /** Taxonomy scenario: two documents in one writer. */
  private static String taxo(FacetsConfig config, String dim1, String dim2) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    Directory taxoDir = new ByteBuffersDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    String result;
    try {
      writer.addDocument(config.build(taxoWriter, taxoDoc(dim1, "v1")));
      writer.addDocument(config.build(taxoWriter, taxoDoc(dim2, "v2")));
      int n = numDocs(writer);
      result = n == 2 ? "ACCEPTED" : "WRONG_NUMDOCS " + n;
    } catch (IllegalArgumentException e) {
      result = "REJECTED " + e.getMessage();
    }
    writer.close();
    taxoWriter.close();
    return result;
  }

  /** Sorted-set doc values scenario: two documents in one writer. */
  private static String ssdv(FacetsConfig config, String dim1, String dim2) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
    String result;
    try {
      writer.addDocument(config.build(ssdvDoc(dim1, "v1")));
      writer.addDocument(config.build(ssdvDoc(dim2, "v2")));
      int n = numDocs(writer);
      result = n == 2 ? "ACCEPTED" : "WRONG_NUMDOCS " + n;
    } catch (IllegalArgumentException e) {
      result = "REJECTED " + e.getMessage();
    }
    writer.close();
    return result;
  }

  /** Persisted scenario: rating-only doc committed, then a new writer gets an author doc. */
  private static String persisted(FacetsConfig config) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    Directory taxoDir = new ByteBuffersDirectory();
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
    writer.addDocument(config.build(taxoWriter, taxoDoc("rating", "v1")));
    writer.commit();
    writer.close();
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
    writer = new IndexWriter(dir, iwc);
    String result;
    try {
      writer.addDocument(config.build(taxoWriter, taxoDoc("author", "v2")));
      int n = numDocs(writer);
      result = n == 2 ? "ACCEPTED" : "WRONG_NUMDOCS " + n;
    } catch (IllegalArgumentException e) {
      result = "REJECTED " + e.getMessage();
    }
    writer.close();
    taxoWriter.close();
    return result;
  }

  public static void main(String[] args) throws Exception {
    String[][] results = {
      {"T1", taxo(config(true, false), "rating", "author")},
      {"T2", taxo(config(true, false), "author", "rating")},
      {"S1", ssdv(config(true, false), "rating", "author")},
      {"S2", ssdv(config(true, false), "author", "rating")},
      {"P1", persisted(config(true, false))},
      {"C1", taxo(config(true, true), "rating", "author")},
      {"C2", taxo(config(false, false), "rating", "author")},
    };
    boolean bug = false;
    boolean controlFailed = false;
    StringBuilder summary = new StringBuilder("SUMMARY");
    for (String[] r : results) {
      System.out.println(r[0] + ": " + r[1]);
      boolean accepted = r[1].equals("ACCEPTED");
      summary.append(' ').append(r[0]).append('=').append(accepted ? "ACCEPTED" : "REJECTED");
      if (r[0].startsWith("C")) {
        controlFailed |= !accepted;
      } else {
        bug |= !accepted;
      }
    }
    System.out.println(summary);
    int status = controlFailed ? 2 : (bug ? 1 : 0);
    System.out.println(
        status == 0
            ? "RESULT: no bug (every document accepted)"
            : status == 1 ? "RESULT: bug present" : "RESULT: a control failed");
    System.exit(status);
  }
}
