# Build only (a plugin of Lucene's own Gradle build, in none of its module jars): since #17035 on main and branch_11x, both unreleased, './gradlew --configuration-cache :lucene:core:compileJava' ends BUILD FAILED with 7 configuration-cache problems where the commit before and the build of the 10.6.0 source tag store an entry; one added up-to-date predicate on compileJava and applyGoogleJavaFormat holds the live regeneration task, so the compile, format and test invocations of Lucene's modules that were run under Gradle's opt-in configuration cache fail (the same compile without the switch succeeds)

Finding LFV-1558 · Area: build / Gradle build infrastructure (build-infra: the regeneration support plugin); developer tooling only, nothing that ships in a module jar · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Build only (a plugin of Lucene's own Gradle build, in none of its module jars). You build Lucene from source on main or branch_11x (both unreleased) with Gradle's configuration cache switched on, for example './gradlew --configuration-cache :lucene:core:compileJava'. You expect what the commit before and the build of the 10.6.0 source tag give: BUILD SUCCESSFUL and 'Configuration cache entry stored'. Instead, since #17035, the command ends BUILD FAILED with '7 problems were found storing the configuration cache' (the compile itself ran, no configuration-cache entry is stored and a second run fails the same way), because one line that the change added to the build's regeneration plugin gives compileJava and applyGoogleJavaFormat an up-to-date predicate whose lambda holds the live regeneration task, which Gradle cannot store. The mode is opt-in, no tracked file of Lucene names it and no workflow uses it; ':lucene:core:check -x test' and a regeneration task did not work under it on the commit before either; the failure is loud, and the same command without the switch, or with --no-configuration-cache, succeeds.

## Expected vs actual

- Expected: You expect what the commit before and the build of the 10.6.0 source tag give: BUILD SUCCESSFUL and 'Configuration cache entry stored'.
- Actual: Since #17035, the command ends BUILD FAILED with '7 problems were found storing the configuration cache' (the compile itself ran, no configuration-cache entry is stored and a second run fails the same way), because one line that the change added to the build's regeneration plugin gives compileJava and applyGoogleJavaFormat an up-to-date predicate whose lambda holds the live regeneration task, which Gradle cannot store.

## Files

- [configuration-cache-compileJava.txt](configuration-cache-compileJava.txt)

## Run it

- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): reproduced through the tree's own Gradle build (12.0.0-SNAPSHOT, Gradle 9.9.1, JDK 25.0.3): the command ends BUILD FAILED with 7 configuration-cache problems, also when it is run a second time; the same compile without the switch, and with --no-configuration-cache, ends BUILD SUCCESSFUL.
- Tested on Lucene branch_11x 75eea467ac52 (a commit of 2026-11-19): reproduced the same way (11.0.0-SNAPSHOT), also when the command is run a second time.
- Build only: the finding is in a plugin of Lucene's own Gradle build, so the repro is one run of that build in a Lucene checkout and not a test against the jars; no test JVM is started, so assertions do not apply. In no release: 11.0.0 is not released, and in a checkout of the tag releases/lucene/10.6.0 the same command ends BUILD SUCCESSFUL with 'Configuration cache entry stored.' (and 'Configuration cache entry reused.' when it is run again). So does a checkout of the commit before #17035: main 1ae70c6e45b8 and branch_11x b98f116b3179. The file configuration-cache-compileJava.txt holds the command and the lines Gradle printed on main f2b061406c4b. Use JDK 25 (java) on the PATH for the command; a first run downloads the Gradle distribution and the build's dependencies if they are not on the machine yet. Runs of 2026-11-20, in a fresh checkout of each commit, on one Linux aarch64 host.
- This finding was re-run on Lucene main 7b8418faa2ff on 2026-11-20: still reproduces.
- How to read the outcome: the finding reproduces when the command ends BUILD FAILED with '7 problems were found storing the configuration cache, 3 of which seem unique' and 'Configuration cache entry discarded with 7 problems.' (Gradle names the task :lucene:core:compileJava and the three types of object it cannot store, QuietExec, JFlexTask and DefaultTask); on the commit before #17035 and at the 10.6.0 tag, a first run of the command in a fresh checkout ends BUILD SUCCESSFUL with 'Configuration cache entry stored.'.

```bash
# use JDK 25 on the PATH
LUCENE=/path/to/lucene   # a clean Lucene checkout of main or of branch_11x
(cd $LUCENE && ./gradlew --configuration-cache :lucene:core:compileJava)   # expected: BUILD SUCCESSFUL, 'Configuration cache entry stored.'; observed: BUILD FAILED, '7 problems were found storing the configuration cache'
```

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
