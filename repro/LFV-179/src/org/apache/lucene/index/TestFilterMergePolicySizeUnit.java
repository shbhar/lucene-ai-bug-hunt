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
package org.apache.lucene.index;

import org.apache.lucene.codecs.CompoundFormat;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * FilterMergePolicy#getSizeUnit() returns super.getSizeUnit() (BYTES) instead of delegating,
 * while FilterMergePolicy#size() delegates (a document count for LogDocMergePolicy). The
 * compound-file decision therefore compares a document count against the byte threshold.
 */
public class TestFilterMergePolicySizeUnit extends LuceneTestCase {

  public void testWrappersReportTheWrappedSizeUnit() {
    LogDocMergePolicy docs = new LogDocMergePolicy();
    assertEquals(MergePolicy.SizeUnit.DOCS, docs.getSizeUnit());
    assertEquals(
        "FilterMergePolicy", MergePolicy.SizeUnit.DOCS, new FilterMergePolicy(docs).getSizeUnit());
    assertEquals(
        "UpgradeIndexMergePolicy",
        MergePolicy.SizeUnit.DOCS,
        new UpgradeIndexMergePolicy(docs).getSizeUnit());
    assertEquals(
        "SoftDeletesRetentionMergePolicy",
        MergePolicy.SizeUnit.DOCS,
        new SoftDeletesRetentionMergePolicy("soft", MatchAllDocsQuery::new, docs).getSizeUnit());
  }

  public void testCompoundFileDecisionChangesWhenWrapped() throws Exception {
    LogDocMergePolicy docs = new LogDocMergePolicy();
    MergePolicy wrapped = new FilterMergePolicy(docs);
    CompoundFormat cf = TestUtil.getDefaultCodec().compoundFormat();
    long mergedDocCount = 1_000_000; // > the 65,536-document threshold for DOCS policies
    assertEquals(
        "same policy, same merged size, different CFS decision",
        cf.useCompoundFile(mergedDocCount, docs),
        cf.useCompoundFile(mergedDocCount, wrapped));
  }
}
