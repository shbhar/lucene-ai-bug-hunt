/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;
/** new RegexpQuery("ab" + "+" x n) for growing n: prints time, outcome; a watchdog exits if one case exceeds 60 s. */
public class RegexpPlusScaleProbe {
  public static void main(String[] args) throws Exception {
    for (int n = 12; n <= 26; n += 2) {
      final int nn = n;
      Thread watchdog = new Thread(() -> {
        try { Thread.sleep(60_000); System.out.println("n=" + nn + " still running after 60 s: giving up"); System.out.flush(); Runtime.getRuntime().halt(3); } catch (InterruptedException e) { }
      });
      watchdog.setDaemon(true);
      watchdog.start();
      String re = "ab" + "+".repeat(n);
      long t0 = System.nanoTime();
      String outcome;
      try {
        RegexpQuery q = new RegexpQuery(new Term("f", re));
        outcome = "built, states=" + q.getAutomaton().getNumStates();
      } catch (Throwable t) {
        outcome = t.getClass().getSimpleName();
      }
      watchdog.interrupt();
      System.out.println("n=" + n + " regexp_length=" + re.length() + " ms=" + (System.nanoTime() - t0) / 1_000_000 + " " + outcome);
      System.out.flush();
    }
  }
}
