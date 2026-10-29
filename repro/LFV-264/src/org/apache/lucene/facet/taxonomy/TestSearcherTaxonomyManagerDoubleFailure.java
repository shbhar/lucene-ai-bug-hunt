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
package org.apache.lucene.facet.taxonomy;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

public class TestSearcherTaxonomyManagerDoubleFailure extends LuceneTestCase {

  public void testTaxonomyFailureSurvivesReaderCloseFailure() throws Exception {
    MockDirectoryWrapper indexDir = newMockDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(indexDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    w.commit();
    tw.commit();
    SearcherTaxonomyManager mgr = new SearcherTaxonomyManager(indexDir, taxoDir, null);

    tw.addCategory(new FacetLabel("a", "b"));
    w.addDocument(new Document());
    tw.commit();
    w.commit();

    taxoDir.deleteFile(SegmentInfos.readLatestCommit(taxoDir).getSegmentsFileName());
    MockDirectoryWrapper.Failure closeFails =
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper dir) throws IOException {
            if (doFail && callStackContains(SegmentReader.class, "doClose")) {
              throw new IOException("simulated close failure");
            }
          }
        };
    indexDir.failOn(closeFails);
    closeFails.setDoFail();

    // expected: the taxonomy's IndexNotFoundException; actual: IllegalArgumentException (self-suppression)
    expectThrows(IndexNotFoundException.class, mgr::maybeRefreshBlocking);

    closeFails.clearDoFail();
    IOUtils.close(w, tw, mgr, indexDir, taxoDir);
  }
}
