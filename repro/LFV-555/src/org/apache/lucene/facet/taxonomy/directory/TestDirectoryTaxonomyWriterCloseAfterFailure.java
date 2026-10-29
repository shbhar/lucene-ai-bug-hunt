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

import java.io.IOException;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDirectoryTaxonomyWriterCloseAfterFailure extends LuceneTestCase {

  public void testCloseAfterFailedCommitReleasesFiles() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir);
    tw.addCategory(new FacetLabel("a"));
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (callStackContains(MockDirectoryWrapper.class, "createOutput")) {
              throw new IOException("simulated disk full");
            }
          }
        });
    expectThrows(IOException.class, tw::commit);
    expectThrows(AlreadyClosedException.class, tw::close);
    // expected: close() released the taxonomy reader; actual: dir.close() says files are still open
    dir.close();
  }
}
