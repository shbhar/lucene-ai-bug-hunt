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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.fst.FSTCompletionLookup;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Public-API probe: a FileDictionary (documented "suggestion TAB weight" format) with one negative
 * weight, fed to FSTCompletionLookup with default settings. Exits 1 if the lower-weighted entry is
 * ranked first. The constructor is chosen reflectively so the same source runs on old releases.
 */
public class FstNegativeWeightProbe {

  static Lookup newLookup() throws Exception {
    Path tmp = Files.createTempDirectory("fstprobe");
    try {
      Constructor<FSTCompletionLookup> c =
          FSTCompletionLookup.class.getConstructor(Directory.class, String.class);
      Directory dir;
      try {
        dir = (Directory) FSDirectory.class.getMethod("open", Path.class).invoke(null, tmp);
      } catch (NoSuchMethodException e) {
        dir = (Directory) FSDirectory.class.getMethod("open", File.class).invoke(null, tmp.toFile());
      }
      return c.newInstance(dir, "fst");
    } catch (NoSuchMethodException e) {
      return FSTCompletionLookup.class.getConstructor().newInstance();
    }
  }

  static List<LookupResult> run(String data) throws Exception {
    Lookup lookup = newLookup();
    lookup.build(new FileDictionary(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
    return lookup.lookup("ap", true, 2);
  }

  public static void main(String[] args) throws Exception {
    List<LookupResult> control = run("apple\t10\napricot\t5\n");
    List<LookupResult> mixed = run("apple\t10\napricot\t-10\n");
    List<LookupResult> allNeg = run("apple\t-1\napricot\t-10\n");
    System.out.println("control  {apple:10, apricot:5}   -> " + control);
    System.out.println("mixed    {apple:10, apricot:-10} -> " + mixed);
    System.out.println("allNeg   {apple:-1, apricot:-10} -> " + allNeg);
    boolean controlOk = control.get(0).key.toString().equals("apple");
    boolean mixedOk = mixed.get(0).key.toString().equals("apple");
    boolean allNegOk = allNeg.get(0).key.toString().equals("apple");
    System.out.println("controlOk=" + controlOk + " mixedOk=" + mixedOk + " allNegOk=" + allNegOk);
    if (!controlOk) {
      System.out.println("UNEXPECTED: control failed");
      System.exit(2);
    }
    if (!mixedOk) {
      System.out.println("BUG: weight 10 ranked below weight -10");
      System.exit(1);
    }
    System.out.println("OK: weights ranked in order");
  }
}
