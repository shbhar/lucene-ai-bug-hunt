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
package history;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * History-sweep driver, phase 1 (works on every nori release, 7.4.0 to main): writes a tiny mecab-ko-dic
 * style source and compiles it with the release's own DictionaryBuilder.main (org.apache.lucene.analysis.ko.dict
 * from 10.0, org.apache.lucene.analysis.ko.util before). DictionaryBuilder.main is the documented entry point
 * of the builder in every release, so it is looked up by name; no private state is touched.
 *
 * <p>args: mode (collision | control), particle tag (J before 10.0, JX from 10.0), input dir, output dir.
 * collision: 가 (NNG) and 는 (particle) share left id 1. control: the same rows with distinct left ids (1, 4).
 *
 * <p>Exit codes: 0 builder accepted the source; 3 builder failed with AssertionError; 4 builder rejected the
 * source with IllegalArgumentException/IllegalStateException; 5 any other error.
 */
public class NoriHistoryBuild {
  public static void main(String[] args) throws Exception {
    String mode = args[0];
    String particle = args[1];
    Path in = Paths.get(args[2]);
    Path out = Paths.get(args[3]);
    int particleLeftId = mode.equals("collision") ? 1 : 4;
    int matrixSize = 5;
    Files.createDirectories(in);
    Files.createDirectories(out);
    List<String> rows =
        Arrays.asList(
            "가,1,1,0,NNG,*,F,가,*,*,*,*",
            "는," + particleLeftId + "," + particleLeftId + ",0," + particle + ",*,T,는,*,*,*,*");
    Files.write(in.resolve("test.csv"), rows, StandardCharsets.UTF_8);
    Files.write(in.resolve("char.def"), Arrays.asList("DEFAULT 0 1 0", "SPACE 0 1 0", "0x0020 SPACE"));
    Files.write(
        in.resolve("unk.def"),
        Arrays.asList("DEFAULT,2,2,10000,SY,*,*,*,*,*,*,*", "SPACE,3,3,0,SP,*,*,*,*,*,*,*"),
        StandardCharsets.UTF_8);
    Files.write(
        in.resolve("matrix.def"),
        Arrays.asList(matrixSize + " " + matrixSize),
        StandardCharsets.US_ASCII);
    System.out.println("source rows: " + rows);

    Class<?> builder;
    try {
      builder = Class.forName("org.apache.lucene.analysis.ko.dict.DictionaryBuilder");
    } catch (ClassNotFoundException e) {
      builder = Class.forName("org.apache.lucene.analysis.ko.util.DictionaryBuilder");
    }
    System.out.println("builder: " + builder.getName());
    Method main = builder.getMethod("main", String[].class);
    try {
      main.invoke(null, (Object) new String[] {in.toString(), out.toString(), "utf-8", "false"});
    } catch (InvocationTargetException wrapped) {
      Throwable t = wrapped.getCause();
      t.printStackTrace(System.out);
      if (t instanceof AssertionError) {
        System.out.println("RESULT builder-assertion-error: " + t);
        System.exit(3);
      }
      if (t instanceof IllegalArgumentException || t instanceof IllegalStateException) {
        System.out.println("RESULT builder-rejected: " + t);
        System.exit(4);
      }
      System.out.println("RESULT builder-other-error: " + t);
      System.exit(5);
    }
    System.out.println("RESULT builder-accepted (mode=" + mode + ")");
  }
}
