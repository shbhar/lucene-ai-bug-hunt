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
package org.apache.lucene.classification.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.grouping.GroupDocs;
import org.apache.lucene.search.grouping.GroupingSearch;
import org.apache.lucene.search.grouping.TopGroups;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Verifier (intended-behaviour devil's advocate) probes for DatasetSplitter with documents that
 * have no class value. Methods marked "mechanism" or "control" are expected to pass; the others
 * assert the documented "split the original index" behaviour.
 */
public class TestDatasetSplitterIntent extends LuceneTestCase {

  enum Dv {
    SORTED,
    SORTED_SET,
    NONE
  }

  private static Document doc(String id, String cls, Dv dv) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new TextField("body", "body of " + id, Field.Store.YES));
    if (cls != null) {
      d.add(new StringField("cls", cls, Field.Store.YES));
      switch (dv) {
        case SORTED -> d.add(new SortedDocValuesField("cls", new BytesRef(cls)));
        case SORTED_SET -> d.add(new SortedSetDocValuesField("cls", new BytesRef(cls)));
        case NONE -> {}
      }
    }
    return d;
  }

  /** Each inner list becomes its own segment (NoMergePolicy, one commit per segment). */
  private static Directory index(Analyzer a, Dv dv, List<List<String[]>> segments)
      throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(a).setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (List<String[]> seg : segments) {
        for (String[] idCls : seg) {
          w.addDocument(doc(idCls[0], idCls[1], dv));
        }
        w.commit();
      }
    }
    return dir;
  }

  private static List<String> ids(Directory dir) throws Exception {
    List<String> out = new ArrayList<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (int i = 0; i < r.maxDoc(); i++) {
        out.add(r.storedFields().document(i).get("id"));
      }
    }
    return out;
  }

  /** Splits and returns the sorted ids found in the three outputs (with multiplicity). */
  private static List<String> split(Directory src, Analyzer a) throws Exception {
    Directory train = new ByteBuffersDirectory();
    Directory test = new ByteBuffersDirectory();
    Directory cv = new ByteBuffersDirectory();
    DirectoryReader reader = DirectoryReader.open(src);
    try {
      new DatasetSplitter(0.1, 0.1).split(reader, train, test, cv, a, false, "cls");
    } finally {
      reader.close();
    }
    List<String> all = new ArrayList<>();
    all.addAll(ids(train));
    all.addAll(ids(test));
    all.addAll(ids(cv));
    Collections.sort(all);
    return all;
  }

  private static List<String[]> seg(String... idClsPairs) {
    List<String[]> s = new ArrayList<>();
    for (int i = 0; i < idClsPairs.length; i += 2) {
      s.add(new String[] {idClsPairs[i], idClsPairs[i + 1]});
    }
    return s;
  }

  /**
   * Mechanism (expected to pass): with the exact GroupingSearch configuration of
   * DatasetSplitter.split, documents without the group field form a null group (documented
   * default of GroupingSearch / FirstPassGroupingCollector), so 2 classes give 3 groups; with the
   * group limit DatasetSplitter computes (2), the class whose first document is last is not
   * returned.
   */
  public void testMechanismNullGroupIsDocumentedGroupingDefault() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    try (Directory src = index(a, Dv.SORTED, List.of(seg("u0", null, "d1", "a", "d2", "b")));
        DirectoryReader r = DirectoryReader.open(src)) {
      IndexSearcher s = new IndexSearcher(r);
      for (int limit : new int[] {3, 2}) {
        GroupingSearch gs = new GroupingSearch("cls");
        gs.setGroupSort(Sort.INDEXORDER);
        gs.setSortWithinGroup(Sort.INDEXORDER);
        gs.setAllGroups(true);
        gs.setGroupDocsLimit(r.maxDoc());
        TopGroups<Object> tg = gs.search(s, MatchAllDocsQuery.INSTANCE, 0, limit);
        List<String> vals = new ArrayList<>();
        for (GroupDocs<Object> g : tg.groups) {
          Object v = g.groupValue();
          vals.add(v == null ? "null" : ((BytesRef) v).utf8ToString());
        }
        assertEquals("allGroups count", Integer.valueOf(3), tg.totalGroupCount);
        if (limit == 3) {
          assertEquals(List.of("null", "a", "b"), vals);
        } else {
          assertEquals(List.of("null", "a"), vals);
        }
      }
    }
  }

  /**
   * Enumerates every single-segment index of 1..5 documents over {unlabelled, a, b, c}. Asserts
   * that no LABELLED document is lost (the narrowest claim, which holds even under the reading
   * that unlabelled documents may legitimately be left out). The message reports the counts.
   */
  public void testEnumerateSingleSegmentLabelledLoss() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    String[] alphabet = {null, "a", "b", "c"};
    long cases = 0,
        withUnlabelled = 0,
        okAll = 0,
        lostOnlyUnlabelled = 0,
        lostLabelled = 0,
        lostWithoutUnlabelled = 0,
        exceptions = 0,
        exceptionsWithSomeLabel = 0;
    String firstLabelledLoss = null;
    for (int n = 1; n <= 5; n++) {
      int total = (int) Math.pow(alphabet.length, n);
      for (int code = 0; code < total; code++) {
        int c = code;
        String[] pairs = new String[2 * n];
        boolean anyUnlabelled = false, anyLabelled = false;
        for (int i = 0; i < n; i++) {
          String cls = alphabet[c % alphabet.length];
          c /= alphabet.length;
          pairs[2 * i] = (cls == null ? "u" : "d") + i;
          pairs[2 * i + 1] = cls;
          if (cls == null) anyUnlabelled = true;
          else anyLabelled = true;
        }
        cases++;
        if (anyUnlabelled) withUnlabelled++;
        try (Directory src = index(a, Dv.SORTED, List.of(seg(pairs)))) {
          List<String> got;
          try {
            got = split(src, a);
          } catch (Throwable t) {
            exceptions++;
            if (anyLabelled) exceptionsWithSomeLabel++;
            continue;
          }
          List<String> want = new ArrayList<>();
          for (int i = 0; i < n; i++) want.add(pairs[2 * i]);
          Collections.sort(want);
          if (got.equals(want)) {
            okAll++;
            continue;
          }
          boolean labelledMissing = false;
          for (String id : want) {
            if (id.startsWith("d") && !got.contains(id)) labelledMissing = true;
          }
          if (!anyUnlabelled) lostWithoutUnlabelled++;
          if (labelledMissing) {
            lostLabelled++;
            if (firstLabelledLoss == null) {
              StringBuilder sb = new StringBuilder();
              for (int i = 0; i < n; i++) sb.append(pairs[2 * i]).append('=').append(pairs[2 * i + 1]).append(' ');
              firstLabelledLoss = sb + "-> " + got;
            }
          } else {
            lostOnlyUnlabelled++;
          }
        }
      }
    }
    String summary =
        "cases="
            + cases
            + " withUnlabelled="
            + withUnlabelled
            + " okAll="
            + okAll
            + " lostLabelled="
            + lostLabelled
            + " lostOnlyUnlabelled="
            + lostOnlyUnlabelled
            + " lostWithoutUnlabelled="
            + lostWithoutUnlabelled
            + " exceptions="
            + exceptions
            + " (with some label: "
            + exceptionsWithSomeLabel
            + ") firstLabelledLoss="
            + firstLabelledLoss;
    System.out.println("V3-ENUM " + summary);
    assertEquals("labelled documents lost; " + summary, 0, lostLabelled);
  }

  /** Every leaf has a labelled document (no NPE path) and a labelled class is still lost. */
  public void testMultiSegmentEveryLeafLabelledStillLoses() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    try (Directory src =
        index(a, Dv.SORTED, List.of(seg("u0", null, "d1", "a"), seg("d2", "b")))) {
      List<String> got = split(src, a);
      assertEquals("every document once", List.of("d1", "d2", "u0"), got);
    }
  }

  /** Control (expected to pass): classes repeated across leaves over-count and mask the loss. */
  public void testControlMultiSegmentRepeatedClassesMask() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    try (Directory src =
        index(
            a,
            Dv.SORTED,
            List.of(seg("u0", null, "d1", "a", "d2", "b"), seg("d3", "a", "d4", "b")))) {
      List<String> got = split(src, a);
      assertEquals("every document once", List.of("d1", "d2", "d3", "d4", "u0"), got);
    }
  }

  /**
   * Sibling probe, out of the candidate's scope: class field with SORTED_SET doc values (LUCENE-7400
   * support), fully labelled, one segment.
   */
  public void testSiblingSortedSetClassFieldFullyLabelled() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    try (Directory src = index(a, Dv.SORTED_SET, List.of(seg("d0", "a", "d1", "b")))) {
      List<String> got = split(src, a);
      assertEquals("every document once", List.of("d0", "d1"), got);
    }
  }

  /**
   * Sibling probe, outside the documented precondition ("must be indexed with sorted doc
   * values"): class field with terms only (the LUCENE-7591 fallback), fully labelled.
   */
  public void testSiblingTermsOnlyClassFieldFullyLabelled() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    try (Directory src = index(a, Dv.NONE, List.of(seg("d0", "a", "d1", "b")))) {
      List<String> got = split(src, a);
      assertEquals("every document once", List.of("d0", "d1"), got);
    }
  }
}
