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
package org.apache.lucene.search.spell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.mockfile.LeakFS;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPlainTextDictionaryPathLeak extends LuceneTestCase {

  // expected: no open handle on the dictionary file after the failed call; actual: one is left open
  public void testFailedIndexDictionaryReleasesDictionaryFile() throws Exception {
    Path tmp = createTempDir();
    LeakFS leakFS = new LeakFS(tmp.getFileSystem());
    Path dictionaryFile = leakFS.wrapPath(tmp).resolve("dict.txt");
    Files.write(dictionaryFile, List.of("alpha"));

    // the unnamed IndexWriter holds the spell index's write lock, so indexDictionary throws
    try (Directory spellIndex = newDirectory();
        SpellChecker spellChecker = new SpellChecker(spellIndex);
        IndexWriter _ = new IndexWriter(spellIndex, new IndexWriterConfig())) {
      expectThrows(
          LockObtainFailedException.class,
          () ->
              spellChecker.indexDictionary(
                  new PlainTextDictionary(dictionaryFile), new IndexWriterConfig(), false));
    }

    leakFS.onClose();
  }
}
