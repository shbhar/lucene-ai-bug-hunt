#!/usr/bin/env python3
"""Unit indexwriter-rollback-throwing-infostream, InfoStream census, step 2 (BY READING, at branch_11x 89e68e0f3a50; the same sites exist on main
1cfa86310f76 with other line numbers in IndexWriter.java): the class of every infoStream.message call of IndexWriter,
DocumentsWriter and DocumentsWriterFlushControl, i.e. what is held at the line that a throw from the log call does not
give back by itself. The guard infoStream.isEnabled(...) in front of each call has the class of its call (118 guards
for 126 calls; no isEnabled call stands alone). Two more tables at the end: the eleven callers of the stall update
(DocumentsWriterFlushControl.updateStallState: isEnabled("DWFC") and the two 'stalling' messages), because the class
depends on the caller, and the one merge-scheduler message that IndexWriter.java makes outside infoStream.message.

Classes:
  N   nothing that outlives a throw: the first statement of an operation, a line after the work is complete, or a line
      under Java monitors only (a throw releases a monitor)
  F   an explicit lock, permit or flag is held, and a catch, a finally or a try-with-resources gives it back
  X   an explicit lock, permit or flag is held and ONE throw at the line skips the release            (cells run)
  X2  the same, but the line is inside the handler of another failure: two faults or a lasting failure (cells run)
  H   a hand-over or a registration is half done at the line; the registered row is named
  C   a log line inside a catch or finally that reports another failure: a throw replaces or joins that failure
  M   merge bookkeeping (a merge thread, or the IndexWriter monitor inside a merge step): a throw goes to the merge's
      own exception path; not read line by line here (an earlier hunt unit injected an Error at every message of a merge script)
A rule = (file, enclosing method, start of the message's first literal, class, what is held / where it is released).
Every site must match exactly one rule, or the script stops.
usage: census_table.py   (reads sites-b11-89e68e0.tsv and sites-main-1cfa863.tsv, writes CENSUS.tsv and CENSUS-SUMMARY.txt)
"""
import collections, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
IW, DW, FC = "IndexWriter.java", "DocumentsWriter.java", "DocumentsWriterFlushControl.java"
PERMIT = "the pool's new-writer permit (lockNewWriters) and the locks of the aborted buffers"
RULES = [
    # ---- IndexWriter
    (IW, "getReader", "flush at getReader", "N", "first statement"),
    (IW, "getReader", "return reader version=", "F", "full-flush mode and the new reader; the catch calls finishFullFlush(false), the outer catch closes the reader (other threads' buffers: LFV-473)"),
    (IW, "getReader", "hit exception during NRT reader", "C", "after finishFullFlush(false)"),
    (IW, "getReader", "getReader took", "N", "the outer catch closes the reader"),
    (IW, "writeSomeDocValuesUpdates", "", "F", "writeDocValuesLock (a ReentrantLock taken by tryLock); unlock in the finally"),
    (IW, "IndexWriter", "init: hit exception on init", "X2", "the directory's write lock; a throw here skips the close of the write lock in the constructor's catch (cell ctor-lasting)"),
    (IW, "IndexWriter", "init", "F", "the directory's write lock; the constructor's catch closes it"),
    (IW, "messageState", "", "F", "called from the constructor (write lock; the catch closes it) and from forceMergeDeletes (nothing held)"),
    (IW, "shutdown", "now flush at close", "F", "the closing flag (shouldClose); the catch runs rollbackInternal, whose handler resets it"),
    (IW, "updateBatch", "hit exception adding batch", "C", "after onTragicEvent; a throw skips maybeCloseOnTragicEvent"),
    (IW, "updateDocuments", "hit exception updating document", "C", "after onTragicEvent; a throw skips maybeCloseOnTragicEvent"),
    (IW, "forceMerge", "", "N", "first statements"),
    (IW, "forceMergeDeletes", "", "N", "after the flush, before any merge is registered"),
    (IW, "rollbackInternalNoCommit", "rollback: done finish merges", "F", "the closing flag, merges disabled; inside the try: the handler closes the writer and releases the write lock (the line upstream's testExceptionDuringRollback throws at)"),
    (IW, "rollbackInternalNoCommit", "rollback: infos=", "F", "the closing flag; inside the try (the handler closes the writer)"),
    (IW, "rollbackInternalNoCommit", "rollback", "X", "the closing flag (set by shouldClose in rollback(), close() and the self-close after an Error) and everything the writer holds (write lock, merge scheduler, pooled readers); the line is IN FRONT OF the try whose handler resets and releases them (cells rollback, rollback-in-close)"),
    (IW, "deleteAll", "hit exception during deleteAll", "C", PERMIT + "; the try-with-resources closes the release closure (see DocumentsWriter 'unlockAllAbortedThread')"),
    (IW, "abortMerges", "", "F", "merges disabled; from deleteAll: re-enabled in a finally, and " + PERMIT + " (released by the closure); from rollback: the closing flag (the handler). Not executed: needs a pending or running merge at that moment"),
    (IW, "waitForMerges", "", "F", "the closing flag (called from shutdown; its catch runs rollbackInternal)"),
    (IW, "publishFlushedSegment", "", "H", "LFV-1082 (the flush ticket is already marked published)"),
    (IW, "addIndexes", "flush at addIndexes(Directory...)", "F", "the write locks of the source directories; the catch closes them"),
    (IW, "addIndexes", "addIndexes: process", "F", "the write locks of the source directories; the catch closes them"),
    (IW, "addIndexes", "flush at addIndexes(CodecReader...)", "N", "first statement"),
    (IW, "addIndexes", "addIndexes(CodecReaders...)", "N", "the three lines with the component 'addIndexes(CodecReaders...)': nothing was merged, or in front of the clean-up loop of the failed merges"),
    (IW, "abortPendingMerges", "", "M", ""),
    (IW, "merge", "", "M", ""),
    (IW, "prepareCommitInternal", "prepareCommit: flush", "N", "commitLock monitor only"),
    (IW, "prepareCommitInternal", "  index before flush", "N", "commitLock monitor only"),
    (IW, "prepareCommitInternal", "hit exception during prepareCommit", "C", "full-flush mode; the finally still calls finishFullFlush"),
    (IW, "prepareCommitInternal", "now run merges during commit", "H", "LFV-456 (the commit's file references are taken and are released only around startCommit)"),
    (IW, "prepareCommitInternal", "done waiting for merges during commit", "H", "LFV-456"),
    (IW, "mergeFinished", "", "M", "merge-on-commit callback"),
    (IW, "commitInternal", "", "N", "commitLock monitor only (a pending commit, if any, stays for commit() or rollback())"),
    (IW, "finishCommit", "commit: pendingCommit != null", "F", "the pending commit and its file references; try-with-resources and finally (an earlier hunt unit executed the commit-path lines)"),
    (IW, "finishCommit", "commit: done writing segments file", "H", "LFV-1410 (after the rename, before the checkpoint)"),
    (IW, "finishCommit", "commit: pendingCommit == null", "N", ""),
    (IW, "finishCommit", "hit exception during finishCommit", "C", ""),
    (IW, "finishCommit", "commit: took", "N", "the commit is complete"),
    (IW, "finishCommit", "commit: done", "N", "the commit is complete"),
    (IW, "doFlush", "hit exception during flush", "C", "after onTragicEvent; a throw skips maybeCloseOnTragicEvent"),
    (IW, "doFlush", "", "N", "before the full flush starts"),
    (IW, "applyAllDeletesAndUpdates", "", "F", "full-flush mode (called inside the full flush of getReader, prepareCommit and flush); the callers' catch or finally leave it"),
    (IW, "commitMergedDeletesAndUpdates", "", "M", ""),
    (IW, "commitMerge", "", "M", ""),
    (IW, "handleMergeException", "", "M", ""),
    (IW, "registerMerge", "reject merge", "N", "before the merge is queued"),
    (IW, "registerMerge", "", "H", "LFV-842 (the merge is queued, its segments are not yet marked)"),
    (IW, "mergeInit", "", "M", ""),
    (IW, "_mergeInit", "", "M", ""),
    (IW, "packageMergedSegment", "", "M", ""),
    (IW, "multiOutputMergeMiddle", "", "M", ""),
    (IW, "mergeMiddle", "", "M", ""),
    (IW, "startCommit", "startCommit: wrote pending segments file", "F", "the commit's file references and the pending segments file; the catch (an earlier hunt unit executed the commit-path lines: nothing leaked after rollback or close)"),
    (IW, "startCommit", "done all syncs", "H", "LFV-1083 (the pending commit is set; the references are released twice)"),
    (IW, "startCommit", "hit exception committing segments file", "C", ""),
    (IW, "startCommit", "", "F", "the commit's file references; the catch releases them"),
    (IW, "onTragicEvent", "hit tragic", "X2", "no lock: the catch around the line swallows the log failure and with it skips tragedy.compareAndSet, so an Error whose log line throws too is not recorded and the writer does not close itself (two faults; not run)"),
    (IW, "testPoint", "", "N", "only with test points enabled"),
    (IW, "nrtIsCurrent", "", "N", ""),
    (IW, "createCompoundFile", "", "N", "before the file is created"),
    (IW, "publishFlushedSegments", "", "H", "LFV-1082"),
    (IW, "incRefDeleter", "", "H", "no lock: the file references of the NRT reader's commit are taken before the line and a throw fails the reader's open without giving them back (a reference leak of the kind of LFV-456; not run; no row names this line)"),
    (IW, "decRefDeleter", "", "N", "after the decRef"),
    (IW, "forceApply", "", "F", "the packet's lock (FrozenBufferedUpdates.lock); unlock in the finally"),
    (IW, "getInfosToApply", "", "F", "the packet's lock (inside forceApply)"),
    (IW, "finishApply", "", "F", "the packet's lock (inside forceApply)"),
    (IW, "closeSegmentStates", "", "F", "the packet's lock (inside forceApply)"),
    # ---- DocumentsWriter
    (DW, "abort", "abort", "N", "inside the try, before the buffers are locked (called under the closing flag from rollback: the handler)"),
    (DW, "abort", "done abort success=true", "N", "every buffer is unlocked again"),
    (DW, "abort", "done abort success=false", "C", ""),
    (DW, "flushOneDWPT", "", "N", "first statement"),
    (DW, "lockAndAbortAll", "lockAndAbortAll", "N", "first statement (LFV-1531's row: executed, loses nothing)"),
    (DW, "lockAndAbortAll", "unlockAllAbortedThread", "X", PERMIT + "; the closure's once-flag is set BEFORE the line, so a second close() does nothing (cell release)"),
    (DW, "lockAndAbortAll", "finished lockAndAbortAll success=true", "F", PERMIT + "; the catch closes the release closure (cell success)"),
    (DW, "lockAndAbortAll", "finished lockAndAbortAll success=false", "X2", PERMIT + "; a throw here skips release.close() (cell abort-lasting)"),
    (DW, "anyChanges", "", "N", ""),
    (DW, "flushAllThreads", "startFullFlush", "N", "before the cut-over"),
    (DW, "flushAllThreads", "", "F", "full-flush mode; the caller's finally calls finishFullFlush"),
    (DW, "finishFullFlush", "", "H", "LFV-1084 (the writer stays in full-flush mode)"),
    # ---- DocumentsWriterFlushControl
    (FC, "updateStallState", "", "by caller", "see the table of the eleven callers"),
    (FC, "findLargestNonPendingWriter", "", "N", "DWFC monitor only (the indexing thread's buffer lock is released by DocumentsWriter.updateDocuments' finally)"),
]
STALL_CALLERS = [
    ("doAfterDocument", 255, "H", "LFV-846's state from another caller: the buffer that became flush-pending is already checked out and registered as flushing when this second stall update of the same add throws (cell stall-doAfterDocument)"),
    ("doAfterFlush", 306, "N", "the buffer is already removed from flushingWriters; notifyAll runs in the nested finally"),
    ("doOnAbort", 401, "H", "LFV-1531 (the aborted buffer is not yet checked out of the pool)"),
    ("checkOutForFlush", 433, "H", "LFV-846 (the registered site)"),
    ("nextPendingFlush", 458, "H", "LFV-846's state from another caller: the buffer polled from the flush queue is lost (cell stall-nextPendingFlush; the row's recorded fix moves this call too)"),
    ("doOnDelete", 500, "N", "DWFC monitor only"),
    ("markForFullFlush", 627, "F", "full-flush mode; the caller's finishFullFlush(false) leaves it and aborts the collected buffers (the class of LFV-473 and #9738)"),
    ("finishFullFlush", 674, "N", "in a finally, after the flags are reset"),
    ("abortFullFlushes", 690, "N", "in a finally, after the flags are reset"),
    ("abortPendingFlushes", 721, "N", "in a finally, after the queues are cleared"),
    ("checkoutLargestNonPendingWriter", 802, "H", "LFV-846's state from another caller: flushNextBuffer()'s buffer is checked out and registered as flushing, and the throw replaces its return (cell stall-checkoutLargest)"),
]
OTHER = [("IndexWriter.java", "7274-7275 (89e68e0f3a50)", "MS", "  checked out merge", "IndexWriterMergeSource.getNextMerge", "H", "LFV-844 (mergeScheduler.verbose() and mergeScheduler.message(...), the one log call of IndexWriter.java that is not an infoStream call)")]


def load(name):
    rows = []
    for l in open(os.path.join(HERE, name), encoding="utf-8").read().split("\n")[1:]:
        if l.strip():
            rows.append(l.split("\t"))
    return rows


b11 = load("sites-b11-89e68e0.tsv")
main = load("sites-main-1cfa863.tsv")
assert len(b11) == len(main) == 126
out = []
used = collections.Counter()
counts = collections.Counter()
for (f, line, comp, text, meth, _chain, mon, blocks), m in zip(b11, main):
    name = meth.split()[-1]
    msg = text.split("> ", 1)[1] if text.startswith("<") and "> " in text else text
    hit = [i for i, r in enumerate(RULES) if r[0] == f and r[1] == name and msg.startswith(r[2])]
    if not hit:
        sys.exit("no rule for %s:%s %s '%s'" % (f, line, name, text))
    i = hit[0]
    used[i] += 1
    cls, note = RULES[i][3], RULES[i][4]
    counts[cls] += 1
    out.append([f, line, m[1], comp, text, name, mon, blocks, cls, note])
unused = [RULES[i][:3] for i in range(len(RULES)) if used[i] == 0]
assert not unused, unused
with open(os.path.join(HERE, "CENSUS.tsv"), "w", encoding="utf-8") as w:
    w.write("file\tline at branch_11x 89e68e0f3a50\tline at main 1cfa86310f76\tcomponent\tmessage (first literal)\tenclosing method\tJava monitors (released by a throw)\ttry/catch/finally/lambda around the line\tclass\twhat is held, and where it is given back\n")
    for r in out:
        w.write("\t".join(r) + "\n")
    w.write("\n# the eleven callers of the stall update (DocumentsWriterFlushControl.updateStallState: the guard isEnabled(\"DWFC\") and the messages 'now stalling flushes' / 'done stalling flushes'), lines at 89e68e0f3a50 = 1cfa86310f76\n")
    w.write("caller\tline of the call\tclass\twhat is held, and where it is given back\n")
    for c in STALL_CALLERS:
        w.write("%s\t%d\t%s\t%s\n" % c)
    w.write("\n# the other log call of IndexWriter.java\n")
    for o in OTHER:
        w.write("\t".join(o) + "\n")
with open(os.path.join(HERE, "CENSUS-SUMMARY.txt"), "w", encoding="utf-8") as w:
    w.write("InfoStream census by reading (unit indexwriter-rollback-throwing-infostream): 126 infoStream.message calls (IndexWriter 110, DocumentsWriter 12, DocumentsWriterFlushControl 4), the same sites on branch_11x 89e68e0f3a50 and main 1cfa86310f76\n")
    for k in ["N", "F", "X", "X2", "H", "C", "M", "by caller"]:
        w.write("  %-9s %3d\n" % (k, counts[k]))
    w.write("stall-update callers: %s\n" % ", ".join("%s=%s" % (c[0], c[2]) for c in STALL_CALLERS))
    w.write("X  (one throw skips the release): %s\n" % "; ".join("%s:%s '%s'" % (r[0], r[1], r[4]) for r in out if r[8] == "X"))
    w.write("X2 (inside the handler of another failure): %s\n" % "; ".join("%s:%s '%s'" % (r[0], r[1], r[4]) for r in out if r[8] == "X2"))
print(open(os.path.join(HERE, "CENSUS-SUMMARY.txt")).read())
