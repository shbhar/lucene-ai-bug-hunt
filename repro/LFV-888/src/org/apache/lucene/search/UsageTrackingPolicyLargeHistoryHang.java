/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.lucene.search;

import org.apache.lucene.index.Term;

/**
 * UsageTrackingQueryCachingPolicy(int historySize) accepts any history size >= 2 and stores it in a
 * FrequencyTrackingRingBuffer, whose IntBag sizes its hash table as maxSize * 3 / 2 in int. For
 * historySize >= 715,827,883 that product overflows, the table gets 2 slots, and the third distinct
 * query hash recorded by onUse probes the full table forever while holding the policy's monitor.
 *
 * <p>Run with a heap of at least 4 GB (the history buffer itself is 2.7 GiB). Exit 0 = the constructor
 * rejected the size with IllegalArgumentException, or every onUse returned; exit 1 = an onUse call did
 * not return within 120 s (hang) or something else was thrown.
 */
public class UsageTrackingPolicyLargeHistoryHang {
  public static void main(String[] args) throws Exception {
    final int historySize = 715_827_883;
    final Throwable[] failure = new Throwable[1];
    final int[] done = new int[1];
    Thread t =
        new Thread(
            () -> {
              try {
                UsageTrackingQueryCachingPolicy policy;
                try {
                  policy = new UsageTrackingQueryCachingPolicy(historySize);
                } catch (IllegalArgumentException rejected) {
                  done[0] = -1; // rejecting an unsupported size up front is acceptable
                  return;
                }
                Query[] queries = {
                  new PrefixQuery(new Term("f", "a")),
                  new PrefixQuery(new Term("f", "b")),
                  new PrefixQuery(new Term("f", "c")),
                };
                for (Query q : queries) {
                  policy.onUse(q);
                  done[0]++;
                }
              } catch (Throwable e) {
                failure[0] = e;
              }
            });
    t.setDaemon(true);
    t.start();
    t.join(120_000);
    if (t.isAlive()) {
      System.out.println(
          "HANG: onUse call #" + (done[0] + 1) + " of 3 did not return within 120 s");
      for (StackTraceElement e : t.getStackTrace()) {
        System.out.println("    at " + e);
      }
      System.exit(1);
    }
    if (failure[0] != null) {
      System.out.println("THREW: " + failure[0]);
      failure[0].printStackTrace(System.out);
      System.exit(1);
    }
    System.out.println(
        done[0] == -1 ? "OK: size rejected up front" : "OK: all 3 onUse calls returned");
  }
}
