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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSearcherLifetimeManagerPruneOrder extends LuceneTestCase {

  public void testPruneKeepsCurrentSearcher() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    SearcherManager mgr = new SearcherManager(w, null);
    SearcherLifetimeManager slm = new SearcherLifetimeManager();
    IndexSearcher older = mgr.acquire();
    w.addDocument(new Document());
    mgr.maybeRefresh();
    IndexSearcher current = mgr.acquire();
    slm.record(current);
    slm.record(older);
    long token = slm.record(current);
    slm.prune(new SearcherLifetimeManager.PruneByAge(0));
    // expected: the current searcher (age 0) survives prune; actual: it is pruned, acquire returns null
    IndexSearcher s = slm.acquire(token);
    assertNotNull(s);
    slm.release(s);
    mgr.release(older);
    mgr.release(current);
    slm.close();
    mgr.close();
    w.close();
    dir.close();
  }
}
