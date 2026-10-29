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

import java.net.URI;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestOrdinalMappingLeafReaderJavadocExample extends LuceneTestCase {

  // Expected: the class-javadoc example compiles; actual: four javac errors (addTaxonomy, IWC, ctor, addIndexes).
  public void testClassJavadocExampleCompiles() throws Exception {
    String source =
        """
        import java.util.List;
        import org.apache.lucene.analysis.Analyzer;
        import org.apache.lucene.facet.taxonomy.OrdinalMappingLeafReader;
        import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
        import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter.MemoryOrdinalMap;
        import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter.OrdinalMap;
        import org.apache.lucene.index.*;
        import org.apache.lucene.store.Directory;
        import org.apache.lucene.util.Version;
        class Example {
          void example(Directory srcTaxoDir, Directory oldDir, Directory newDir, Version VER, Analyzer ANALYZER)
              throws Exception {
        OrdinalMap map = new MemoryOrdinalMap();
        DirectoryTaxonomyWriter.addTaxonomy(srcTaxoDir, map);
        int[] ordmap = map.getMap();
        DirectoryReader reader = DirectoryReader.open(oldDir);
        IndexWriterConfig conf = new IndexWriterConfig(VER, ANALYZER);
        IndexWriter writer = new IndexWriter(newDir, conf);
        List<LeafReaderContext> leaves = reader.leaves();
        LeafReader wrappedLeaves[] = new LeafReader[leaves.size()];
        for (int i = 0; i < leaves.size(); i++) {
          wrappedLeaves[i] = new OrdinalMappingLeafReader(leaves.get(i).reader(), ordmap);
        }
        writer.addIndexes(new MultiReader(wrappedLeaves));
        writer.commit();
          }
        }
        """;
    JavaFileObject unit =
        new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
          }
        };
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> options =
        List.of(
            "-classpath", System.getProperty("java.class.path"),
            "-d", createTempDir().toString(),
            "-proc:none");
    boolean compiled =
        ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, diagnostics, options, null, List.of(unit))
            .call();
    assertTrue(diagnostics.getDiagnostics().toString(), compiled);
  }
}
