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
package org.apache.lucene.facet.taxonomy.directory;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * When IndexWriter rejects a category document with a non-aborting exception (here: a field added
 * through the documented ordinal-document enrichment hook), the document still takes a docID and is
 * marked deleted, but DirectoryTaxonomyWriter does not advance its ordinal counter. Every category
 * added afterwards is handed an ordinal that is one less than its docID, so a taxonomy reader maps
 * those ordinals to other categories. TaxonomyWriter.addCategory "returns its ordinal"; the
 * taxonomy reader's ordinal of a category is its docID.
 */
public class TestTaxonomyWriterRejectedOrdinalDocument extends LuceneTestCase {

  /**
   * Every ordinal that addCategory returned must be the reader's ordinal of that category and a
   * valid ordinal of the reader (below getSize()). A writer that refuses further additions after
   * the rejection (returns no ordinal) does not violate this.
   */
  private static void assertWriterAndReaderAgree(Directory dir, String[] labels, int[] ordinals)
      throws Exception {
    try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
      for (int i = 0; i < labels.length; i++) {
        if (ordinals[i] < 0) {
          continue; // the writer refused the addition
        }
        FacetLabel label = new FacetLabel(labels[i]);
        String msg =
            "addCategory returned "
                + ordinals[i]
                + " for "
                + label
                + "; the taxonomy reader (size "
                + reader.getSize()
                + ") says ordinal "
                + ordinals[i]
                + " is "
                + safePath(reader, ordinals[i])
                + " and "
                + label
                + " is ordinal "
                + reader.getOrdinal(label);
        assertEquals(msg, reader.getOrdinal(label), ordinals[i]);
        assertTrue(msg, ordinals[i] < reader.getSize());
      }
    }
  }

  /** addCategory, or -1 if the writer refuses (throws) after the earlier rejection. */
  private static int addOrRefused(DirectoryTaxonomyWriter tw, String label) {
    try {
      return tw.addCategory(new FacetLabel(label));
    } catch (Exception e) {
      return -1;
    }
  }

  private static String safePath(DirectoryTaxonomyReader reader, int ordinal) {
    try {
      return String.valueOf(reader.getPath(ordinal));
    } catch (RuntimeException | java.io.IOException e) {
      return e.toString();
    }
  }

  /** The enrichment of one category carries a term that exceeds the indexed term length limit. */
  public void testImmenseEnrichmentTermShiftsLaterOrdinals() throws Exception {
    Directory dir = newDirectory();
    String immense = "x".repeat(40_000);
    ReindexingEnrichedDirectoryTaxonomyWriter tw =
        new ReindexingEnrichedDirectoryTaxonomyWriter(
            dir,
            (label, doc) -> {
              if (label.length > 0) {
                String desc = label.components[0].equals("rejected") ? immense : "ok";
                doc.add(new StringField("description", desc, Field.Store.NO));
              }
            });
    int a = tw.addCategory(new FacetLabel("a"));
    tw.commit();
    expectThrows(IllegalArgumentException.class, () -> tw.addCategory(new FacetLabel("rejected")));
    int b = addOrRefused(tw, "b");
    int c = addOrRefused(tw, "c");
    tw.close();
    assertWriterAndReaderAgree(dir, new String[] {"a", "b", "c"}, new int[] {a, b, c});
    dir.close();
  }

  /** The enrichment of one category carries a vector of a different dimension. */
  public void testVectorDimensionMismatchShiftsLaterOrdinals() throws Exception {
    Directory dir = newDirectory();
    ReindexingEnrichedDirectoryTaxonomyWriter tw =
        new ReindexingEnrichedDirectoryTaxonomyWriter(
            dir,
            (label, doc) -> {
              if (label.length > 0) {
                int dims = label.components[0].equals("rejected") ? 3 : 2;
                doc.add(new KnnFloatVectorField("embedding", new float[dims]));
              }
            });
    int a = tw.addCategory(new FacetLabel("a"));
    tw.commit();
    expectThrows(IllegalArgumentException.class, () -> tw.addCategory(new FacetLabel("rejected")));
    int b = addOrRefused(tw, "b");
    tw.close();
    assertWriterAndReaderAgree(dir, new String[] {"a", "b"}, new int[] {a, b});
    dir.close();
  }
}
