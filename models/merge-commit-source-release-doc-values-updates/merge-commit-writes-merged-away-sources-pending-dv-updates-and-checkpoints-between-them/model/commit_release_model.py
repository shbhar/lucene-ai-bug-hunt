#!/usr/bin/env python3
"""Explicit-state model of the file references around IndexWriter.commitMerge's release of its source segments.

Mapping (Lucene main 2cf3f5776d9e = branch_11x 75eea467ac52 for these lines unless noted):
  refs['ckpt']            IndexFileDeleter.checkpoint(segmentInfos, false): incRef the files of the segments in the
                          index, decRef the files of the previous checkpoint (lastFiles)      IndexFileDeleter.checkpoint
  refs['merge'][s]        deleter.incRef(mr.reader.getSegmentInfo().files()) when the merge pulls the reader of
                          source s: the files the source had THEN                             IndexWriter.java:5653 (initMergeReaders)
  write during the merge  flush() -> writeReaderPool -> ReadersAndUpdates.writeFieldUpdates   IndexWriter.java (doFlush), ReadersAndUpdates.java:859
  pending[s]              a resolved update that no flush has written yet (the gated flush)   ReadersAndUpdates.addDVUpdate :153
  commit, step 1          the merged segment's carried-over updates are written and
                          checkpointed while the sources are still in the index              IndexWriter.java:5029 release(mergedUpdates,false)
  commit, step 2          segmentInfos.applyMergeChanges: the sources leave the index         IndexWriter.java:5048
  commit, step 3          closeMergeReaders, per source in merge order:                       IndexWriter.java:5465-5491
     dropChanges()          drops deletes only, pending doc-values updates stay               ReadersAndUpdates.java:276-285
     release(rld)           pooling off and last reference: writeFieldUpdates                  ReaderPool.java:202-218
        fold                  priorGens.length >= maxDocValuesOverlays: every prior delta
                              generation is OPENED BY NAME                                    ReadersAndUpdates.java:368-384
        after a write         IndexWriter.release -> checkpointNoSIS()                         IndexWriter.java:6875-6884
     deleter.decRef(the files the merge reader had)                                           IndexWriter.java:5489
  commit, step 4          checkpoint()                                                        IndexWriter.java:5068-5073
A file is deleted when its reference count reaches zero (IndexFileDeleter.decRef).

The model answers one question per cell: does a fold in step 3 open a file that is already deleted?
"""
import itertools
import json
import sys

LIMIT_DEFAULT = 8


def run_cell(pooling, overlays, pre, during, last, fixed=False):
    """Returns 'OK' or 'TRAGIC <file>' for two one-document sources released in merge order (first, second)."""
    sources = ['first', 'second']
    files = set()            # the directory
    count = {}               # IndexFileDeleter reference counts
    last_files = set()       # the files of the last checkpoint
    index = list(sources)    # segments in segmentInfos
    seg_files = {s: {s + '.core'} for s in sources}      # SegmentCommitInfo.files()
    deltas = {s: [] for s in sources}                    # overlay delta generations, oldest first
    gen = {s: 0 for s in sources}
    files |= {s + '.core' for s in sources}

    def inc(fs):
        for f in fs:
            count[f] = count.get(f, 0) + 1

    def dec(fs):
        for f in fs:
            count[f] -= 1
            if count[f] == 0:
                files.discard(f)

    def checkpoint():
        nonlocal last_files
        now = set()
        for s in index:
            now |= seg_files[s]
        inc(now)
        dec(last_files)
        last_files = now

    def write_updates(s):
        """ReadersAndUpdates.writeFieldUpdates for one field; returns the missing file or None."""
        gen[s] += 1
        name = '%s_gen%d' % (s, gen[s])
        if overlays > 0:
            if len(deltas[s]) >= overlays:
                for g in reversed(deltas[s]):            # newest first, as executed (the file named is the newest)
                    f = '%s_gen%d' % (s, g)
                    if f not in files:
                        return f
                # fold: the new generation replaces the prior deltas
                seg_files[s] = {s + '.core', name}
                deltas[s] = [gen[s]]
            else:
                seg_files[s] = seg_files[s] | {name}
                deltas[s] = deltas[s] + [gen[s]]
        else:
            # dense rewrite: reads the column through the open pooled reader, opens nothing by name
            seg_files[s] = {s + '.core', name}
        files.add(name)
        return None

    checkpoint()
    for _ in range(pre):                                 # rounds before the merge
        for s in sources:
            assert write_updates(s) is None
        checkpoint()
    merge_refs = {s: set(seg_files[s]) for s in sources}  # the merge pulls its readers
    for s in sources:
        inc(merge_refs[s])
    for _ in range(during):                              # rounds while the merge runs
        for s in sources:
            assert write_updates(s) is None
        checkpoint()
    pending = {'first': last in ('both', 'first'), 'second': last in ('both', 'second')}

    # commitMerge
    seg_files['merged'] = {'merged.core', 'merged_gen1'}
    files |= seg_files['merged']
    checkpoint()                                         # step 1 (the sources are still in the index)
    index = ['merged']                                   # step 2
    for s in sources:                                    # step 3
        if fixed:
            pending[s] = False                           # the fix sketch: dropChanges also drops the updates
        if not pooling and pending[s]:
            missing = write_updates(s)
            if missing is not None:
                return 'TRAGIC ' + missing
            checkpoint()
        dec(merge_refs[s])
    checkpoint()                                         # step 4
    return 'OK'


def cells():
    for pooling, overlays, (pre, during), last in itertools.product(
            (False, True), (LIMIT_DEFAULT, 0), ((0, 8), (8, 0), (7, 1), (0, 7)), ('both', 'first', 'second')):
        yield dict(pooling=pooling, overlays=overlays, pre=pre, during=during, last=last)


if __name__ == '__main__':
    fixed = '--fixed' in sys.argv
    out = []
    for c in cells():
        r = run_cell(fixed=fixed, **c)
        out.append(dict(c, predicted=r))
    json.dump(out, sys.stdout, indent=1)
    print()
    print('cells %d, predicted TRAGIC %d' % (len(out), sum(1 for o in out if o['predicted'] != 'OK')), file=sys.stderr)
