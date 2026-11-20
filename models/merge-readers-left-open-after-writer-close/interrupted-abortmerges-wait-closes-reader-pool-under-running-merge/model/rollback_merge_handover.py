#!/usr/bin/env python3
"""Explicit-state model: one merge at the pull of its reader against one thread that closes the writer.

All interleavings of the two actors are enumerated for each configuration; the invariant is checked in every terminal
state: no SegmentReader reference is left once both actors have ended (reader_refs == 0).

Model element -> code (branch_11x 75eea467ac52, lucene/core/src/java/org/apache/lucene/index/):
  merge m_check     IndexWriter.mergeMiddle: merge.checkAborted()                              IndexWriter.java:5987
  merge m_pool      IndexWriter.initMergeReaders: getPooledInstance(sci, true), no writer lock IndexWriter.java:5650
                    (IndexWriter.getPooledInstance: ensureOpen(false), then ReaderPool.get:     ReaderPool.java:397-433
                     AlreadyClosedException on a closed pool, else the entry is created and gets the pool's and the
                     caller's reference)
  merge m_open      synchronized (writer) { rld.getReaderForMerge(...) }: no open/closed check  IndexWriter.java:5651-5654
                    (ReadersAndUpdates.getReader: the entry's reference and the caller's)       ReadersAndUpdates.java:188-198
  merge m_work      the merge runs; it sees its abort flag at a later checkAborted, or commits
  merge m_close     IndexWriter.closeMergeReaders: getPooledInstance(info, false) FIRST         IndexWriter.java:5477
                    (throws on a closed writer or pool: nothing below runs), rld.release(sr),   IndexWriter.java:5483-5484
                    release(rld) -> ReaderPool.release drops the entry and its reader when the  ReaderPool.java:193-228
                    pool holds the last reference and reader pooling is off
  merge m_finish    IndexWriter.merge finally: closeMergeReaders, then mergeFinish(merge)       IndexWriter.java:5149-5155
                    (in the code mergeFinish also runs after m_close threw: the call that throws is
                    the one in mergeMiddle's finally, IndexWriter.java:6223, and this second call finds
                    the merge finished; the model ends the merge at m_close then, which changes no
                    reader count)
  closer c_abort    rollbackInternalNoCommit: synchronized (writer) { abortMerges() }:          IndexWriter.java:2618-2624
                    merge.setAborted() for running merges                                       IndexWriter.java:2839-2844
  closer c_wait     abortMerges: while (runningMerges.size() ... != 0) doWait()                 IndexWriter.java:2849-2861
  closer c_interrupt doWait: wait(1000) throws InterruptedException -> ThreadInterruptedException IndexWriter.java:6271-6283
  closer c_sched    mergeScheduler.close(): ConcurrentMergeScheduler.close() -> sync() joins    ConcurrentMergeScheduler.java:466-511
                    every merge thread; SerialMergeScheduler.close() is empty                   SerialMergeScheduler.java
  closer c_later    docWriter.abort() and the steps after it; may throw                         IndexWriter.java:2633-2640
  closer c_pool     readerPool.close() (normal path :2677; failure branch :2709-2710):          ReaderPool.java:231-236, 333-360
                    dropAll -> every entry's dropReaders (the entry's reference on its reader), ReadersAndUpdates.java:213-225
                    then closed = true
Configurations: scheduler close waits for the merge (ConcurrentMergeScheduler) or not (SerialMergeScheduler, the merge
runs on another application thread); the closer is interrupted while it waits, or not; a later step of the normal path
throws, or not. Reader pooling off; one segment; one merge.
Run: python3 -I rollback_merge_handover.py > rollback_merge_handover.out
"""
import itertools
import sys


def initial():
    return dict(
        m="check", c="abort", aborted=False, running=True, merge_thread_done=False,
        entry_in_pool=False, entry_refs=0, reader_refs=0, merge_holds_reader=False,
        pool_closed=False, writer_closed=False, interrupted=False, failure_branch=False,
        merge_exc="", trace=())


def merge_steps(s, cfg):
    m = s["m"]
    if m == "check":
        t = dict(s)
        if s["aborted"]:
            t["m"] = "close"  # MergeAbortedException before any reader
            return [("m_check(aborted)", t)]
        t["m"] = "pool"
        return [("m_check", t)]
    if m == "pool":
        t = dict(s)
        if s["writer_closed"] or s["pool_closed"]:
            t["m"] = "close"
            t["merge_exc"] = "AlreadyClosedException at getPooledInstance(create)"
            return [("m_pool(ACE)", t)]
        t["entry_in_pool"] = True
        t["entry_refs"] = 2  # the pool's and the caller's
        t["m"] = "open"
        return [("m_pool", t)]
    if m == "open":
        t = dict(s)
        t["reader_refs"] = 2  # the entry's and the merge's
        t["merge_holds_reader"] = True
        t["m"] = "work"
        return [("m_open", t)]
    if m == "work":
        t = dict(s)
        t["m"] = "close"
        return [("m_work", t)]
    if m == "close":
        t = dict(s)
        if s["merge_holds_reader"]:
            if s["writer_closed"] or s["pool_closed"]:
                # getPooledInstance(info, false) throws first: the reader is not released (the model ends the merge here)
                t["merge_exc"] = "AlreadyClosedException in closeMergeReaders"
                t["m"] = "done"
                t["merge_thread_done"] = True
                return [("m_close(ACE, reader kept)", t)]
            t["reader_refs"] -= 1  # rld.release(sr)
            t["merge_holds_reader"] = False
            t["entry_refs"] -= 1  # release(rld)
            if t["entry_refs"] == 1 and t["entry_in_pool"]:
                t["reader_refs"] -= 1  # dropReaders
                t["entry_refs"] = 0
                t["entry_in_pool"] = False
        t["m"] = "finish"
        return [("m_close", t)]
    if m == "finish":
        t = dict(s)
        t["running"] = False
        t["m"] = "done"
        t["merge_thread_done"] = True
        return [("m_finish", t)]
    return []


def pool_close(t):
    if t["entry_in_pool"]:
        if t["reader_refs"] > 0:
            t["reader_refs"] -= 1  # the entry's reference on its reader
        t["entry_refs"] -= 1
        t["entry_in_pool"] = False
    t["pool_closed"] = True
    t["writer_closed"] = True


def closer_steps(s, cfg):
    c = s["c"]
    out = []
    if c == "abort":
        t = dict(s)
        t["aborted"] = True
        t["c"] = "wait"
        return [("c_abort", t)]
    if c == "wait":
        if not s["running"]:
            t = dict(s)
            t["c"] = "sched"
            out.append(("c_wait(done)", t))
        elif cfg["interrupt"] and not s["interrupted"]:
            t = dict(s)
            t["interrupted"] = True
            t["failure_branch"] = True
            t["c"] = "sched"
            out.append(("c_interrupt", t))
        return out
    if c == "sched":
        if cfg["scheduler_waits"] and not s["merge_thread_done"]:
            return []  # ConcurrentMergeScheduler.sync() joins the merge thread
        t = dict(s)
        t["c"] = "pool" if s["failure_branch"] else "later"
        return [("c_sched", t)]
    if c == "later":
        t = dict(s)
        if cfg["later_step_throws"]:
            t["failure_branch"] = True
            t["c"] = "sched2"
            return [("c_later(throws)", t)]
        t["c"] = "pool"
        return [("c_later", t)]
    if c == "sched2":
        if cfg["scheduler_waits"] and not s["merge_thread_done"]:
            return []
        t = dict(s)
        t["c"] = "pool"
        return [("c_sched(again)", t)]
    if c == "pool":
        t = dict(s)
        pool_close(t)
        t["c"] = "done"
        return [("c_pool", t)]
    return []


def explore(cfg):
    start = initial()
    stack = [start]
    seen = set()
    terminals = 0
    bad = []
    deadlocks = 0
    while stack:
        s = stack.pop()
        key = tuple(sorted((k, v) for k, v in s.items() if k != "trace"))
        if key in seen:
            continue
        seen.add(key)
        succ = merge_steps(s, cfg) + closer_steps(s, cfg)
        if not succ:
            if s["m"] == "done" and s["c"] == "done":
                terminals += 1
                if s["reader_refs"] != 0:
                    bad.append(s)
            else:
                deadlocks += 1
            continue
        for name, t in succ:
            t["trace"] = s["trace"] + (name,)
            stack.append(t)
    return len(seen), terminals, bad, deadlocks


def main():
    total_bad = 0
    for scheduler_waits, interrupt, later in itertools.product([True, False], [False, True], [False, True]):
        cfg = dict(scheduler_waits=scheduler_waits, interrupt=interrupt, later_step_throws=later)
        states, terminals, bad, deadlocks = explore(cfg)
        name = "%s, closer %s, a later rollback step %s" % (
            "scheduler close waits for the merge (ConcurrentMergeScheduler)" if scheduler_waits
            else "scheduler close does not wait (SerialMergeScheduler)",
            "interrupted in its wait" if interrupt else "not interrupted",
            "throws" if later else "does not throw")
        print("CONFIG %s: states=%d terminal=%d with an open reader=%d stuck=%d"
              % (name, states, terminals, len(bad), deadlocks))
        total_bad += len(bad)
        shown = set()
        for s in sorted(bad, key=lambda x: len(x["trace"])):
            shape = "pool closed before the reader was opened" if s["trace"].index("c_pool") < s["trace"].index("m_open") \
                else "pool closed while the merge held its reader"
            if shape in shown:
                continue
            shown.add(shape)
            print("  COUNTEREXAMPLE (%s; reader references left=%d; merge thread: %s):" % (shape, s["reader_refs"], s["merge_exc"]))
            print("    " + " -> ".join(s["trace"]))
    print("RESULT: %s" % ("an open reader is reachable in the configurations above" if total_bad else "no open reader"))


if __name__ == "__main__":
    sys.exit(main())
