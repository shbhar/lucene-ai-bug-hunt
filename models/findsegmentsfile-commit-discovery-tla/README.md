# Formal models and scripts: findsegmentsfile-commit-discovery-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- DirectoryReader.listCommits throws CorruptIndexException (CorruptSegmentInfoException on main) on a healthy index when the deletion policy deletes an older commit while listCommits is reading it (known: #8894 / LUCENE-7843) (released since 6.5.0) (finding LFV-1095; [repro](../../repro/LFV-1095/))
- SegmentInfos.FindSegmentsFile's double listAll() check misses a weakly consistent listing when each of its two listings overlaps one complete commit, so opening a healthy index can throw IndexNotFoundException (residual of open #3659 / LUCENE-2585; shown only with a simulated weakly consistent listing) (finding LFV-1092; [repro](../../repro/LFV-1092/))

## Files

- [findsegmentsfile-double-listall-misses-commits/model/FindSegments.tla](findsegmentsfile-double-listall-misses-commits/model/FindSegments.tla) (used for LFV-1092)
- [findsegmentsfile-double-listall-misses-commits/model/MC.tla](findsegmentsfile-double-listall-misses-commits/model/MC.tla) (used for LFV-1092)
- [findsegmentsfile-double-listall-misses-commits/model/MC_weak_open.cfg](findsegmentsfile-double-listall-misses-commits/model/MC_weak_open.cfg) (used for LFV-1092)
- [findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_keep2.cfg](findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_keep2.cfg) (used for LFV-1092)
- [findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_meta4.cfg](findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_meta4.cfg) (used for LFV-1092)
- [findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_onecommit.cfg](findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_onecommit.cfg) (used for LFV-1092)
- [findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_other.cfg](findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_other.cfg) (used for LFV-1092)
- [findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_single.cfg](findsegmentsfile-double-listall-misses-commits/model/MC_weak_open_single.cfg) (used for LFV-1092)
- [findsegmentsfile-double-listall-misses-commits/model/MC_weak_reopen.cfg](findsegmentsfile-double-listall-misses-commits/model/MC_weak_reopen.cfg) (used for LFV-1092)
- [listcommits-older-commit-deleted-mid-read-corrupt/model/FindSegments.tla](listcommits-older-commit-deleted-mid-read-corrupt/model/FindSegments.tla) (used for LFV-1095)
- [listcommits-older-commit-deleted-mid-read-corrupt/model/MC.tla](listcommits-older-commit-deleted-mid-read-corrupt/model/MC.tla) (used for LFV-1095)
- [listcommits-older-commit-deleted-mid-read-corrupt/model/MC_atomic_list.cfg](listcommits-older-commit-deleted-mid-read-corrupt/model/MC_atomic_list.cfg) (used for LFV-1095)
- [listcommits-older-commit-deleted-mid-read-corrupt/model/MC_atomic_list_fix.cfg](listcommits-older-commit-deleted-mid-read-corrupt/model/MC_atomic_list_fix.cfg) (used for LFV-1095)
- [listcommits-older-commit-deleted-mid-read-corrupt/model/MC_atomic_list_keep2.cfg](listcommits-older-commit-deleted-mid-read-corrupt/model/MC_atomic_list_keep2.cfg) (used for LFV-1095)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).
