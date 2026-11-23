# Release tooling only (a hand-run developer script of the source tree, not a library class): dev-tools/scripts/diff_lucene_changes.py starts diff on two temporary files it has not flushed, so in the runs where both CHANGES.txt sections still sat in the files' write buffers (Linux, Python 3.9 to 3.15) it printed no diff lines although the sections differ

Finding LFV-1557 · Area: release tooling / dev-tools/scripts (diff_lucene_changes.py, a hand-run helper that compares one release's section of lucene/CHANGES.txt between two branches or local clones); a developer script of the source tree, no Lucene module · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Release tooling only: a hand-run developer script of the source tree; no jar, API, index or search result is involved. You run dev-tools/scripts/diff_lucene_changes.py as its usage line says, with two branches or local clones and a release name, and expect it to print the differences between the two copies of that release's CHANGES.txt section (its docstring: 'A simple tool to see diffs between main's version of CHANGES.txt entries for a given release vs the stable branch's version'). Instead it printed no diff lines for two sections of 12 bytes each that differ in one line, which is also its output for identical sections, with exit code 0 either way: the script writes each section into a temporary file and starts diff on the two file names without flushing the files, and both sections were still in the files' write buffers, so diff compared two files of 0 bytes. Run on Linux (4,096-byte file-system blocks, GNU diff 3.8) under Python 3.12.16 with the script of branch_11x 5d7ca54ba503, of main d7162870c1fd and of the 10.6.0 and 10.0.0 source tags: the sections of up to 4,096 bytes that were run were hidden in this way, and the longer ones (4,097 and 8,192 bytes; the 11.0.0 sections of branch_11x and main, 28,577 and 28,571 bytes) were compared correctly; under Python 3.14 and 3.15 the measured limit was 131,071 bytes. With two added flush calls the script printed the true difference in every run made with it. Not new in 11.0: the source trees of 10.0.0 to 10.6.0 have the script too. No release is known to have gone wrong because of it.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It printed no diff lines for two sections of 12 bytes each that differ in one line, which is also its output for identical sections, with exit code 0 either way: the script writes each section into a temporary file and starts diff on the two file names without flushing the files, and both sections were still in the files' write buffers, so diff compared two files of 0 bytes.

## Files

- [src/test_diff_lucene_changes.py.txt](src/test_diff_lucene_changes.py.txt)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene branch_11x 5d7ca54ba503 (a commit of 2026-11-19): reproduced.
- The finding is in a Python script of the source tree, so its repro is a Python test and not a JUnit test: no JDK, jar or build is involved, and there is no run with or without Java assertions. The test makes two folders that stand for a stable branch and for main, whose lucene/CHANGES.txt files differ in one line of the section of release 9.9.1 (12 bytes each), runs dev-tools/scripts/diff_lucene_changes.py from the root of the checkout as its usage line says, and requires the diff lines of that one line in the script's output. The recorded runs used Python 3.12.16 and GNU diff 3.8 on Linux, with the temporary folder on a file system with 4,096-byte blocks; which sections stay hidden depends on the Python version and on that block size (the finding's record gives the measured limits), not on the Lucene version. The test file is shipped with the ending .txt; Python runs it as it is.
- How to read the outcome: the finding reproduces when the run ends with FAILED (failures=1): for two sections that differ in one line the script printed its two NOTE lines and no diff line; a script that flushes its two temporary files before it starts diff ends the run with OK.

```bash
# needs python3 (3.12 or newer, as the README of dev-tools/scripts asks) and diff on the PATH; no JDK, no build
LUCENE=/path/to/lucene   # a Lucene checkout: main, branch_11x or a release tag from 10.0.0 on
T="$PWD/src/test_diff_lucene_changes.py.txt"   # one Python unittest, shipped with the ending .txt
(cd "$LUCENE" && python3 -B "$T")   # expected: OK; observed: FAILED (failures=1)
```

Formal models and scripts this finding's part of the hunt used: [../../models/diff-lucene-changes-unflushed-temp-files/](../../models/diff-lucene-changes-unflushed-temp-files/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
