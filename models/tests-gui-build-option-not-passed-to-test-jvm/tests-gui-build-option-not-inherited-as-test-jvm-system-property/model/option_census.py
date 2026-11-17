#!/usr/bin/env python3
"""Option census of Lucene's test build plugin, by reading (unit tests-gui-build-option-not-passed-to-test-jvm).

Model element -> source line (branch_11x daa54f2e0cd3; the same blob 0fc4bd79ad on main 781025d6fe32):
  declared option        buildOptions.add[Boolean|Int|Dir]Option("NAME", ...)      TestsAndRandomizationPlugin.java:130-322, 635-642
  inherited option       optionsInheritedAsProperties.add("NAME") / .addAll(List.of(...))   :175-322, 644
  forwarding loop        for (String key : optionsInheritedAsProperties) ... task.systemProperty(key, ...)   :506-513
  test-JVM reader        System.getProperty("NAME") / systemPropertyAs*("NAME", ...) / @TestGroup(sysProperty = ...)
                         under lucene/ (test framework, tests), and the runner's own names (randomizedtesting SysGlobals)

usage: option_census.py <git dir of a full clone> <tree-ish> [<tree-ish> ...]
Reads with `git show` / `git grep` only; writes nothing; prints one report per tree to stdout.
"""
import re
import subprocess
import sys

PLUGIN = "build-tools/build-infra/src/main/java/org/apache/lucene/gradle/plugins/java/TestsAndRandomizationPlugin.java"
OLD_SCRIPT = "gradle/testing/randomization.gradle"


def git(repo, *args, ok=(0,)):
    p = subprocess.run(["git", "-C", repo, "--no-pager", *args], capture_output=True, text=True)
    if p.returncode not in ok:
        return None
    return p.stdout


def line_of(text, pos):
    return text.count("\n", 0, pos) + 1


def census(repo, tree):
    out = []
    sha = (git(repo, "rev-parse", "--short=12", tree + "^{commit}") or "?").strip()
    out.append("=" * 100)
    out.append("tree %s (commit %s)" % (tree, sha))
    src = git(repo, "show", "%s:%s" % (tree, PLUGIN))
    if src is None:
        old = git(repo, "show", "%s:%s" % (tree, OLD_SCRIPT))
        out.append("  no %s in this tree" % PLUGIN)
        if old is not None:
            names = re.findall(r"propName:\s*'([^']+)'", old)
            passes_all = "systemProperties testOptionsResolved" in old
            out.append("  old script %s: %d options declared; 'systemProperties testOptionsResolved' present: %s"
                       % (OLD_SCRIPT, len(names), passes_all))
            out.append("  tests.gui declared there: %s (every resolved option is passed to the test JVM)"
                       % ("tests.gui" in names))
        return out
    blob = (git(repo, "rev-parse", "--short=10", "%s:%s" % (tree, PLUGIN)) or "?").strip()
    out.append("  plugin blob %s" % blob)

    declared = []  # (name, kind, line)
    for m in re.finditer(r"\.\s*add(Boolean|Int|Dir)?Option\(\s*\"([^\"]+)\"", src):
        declared.append((m.group(2), m.group(1) or "String", line_of(src, m.start())))
    inherited = {}
    for m in re.finditer(r"optionsInheritedAsProperties\s*\.\s*add\(\s*\"([^\"]+)\"\s*\)", src):
        inherited[m.group(1)] = line_of(src, m.start())
    for m in re.finditer(r"optionsInheritedAsProperties\s*\.\s*addAll\(\s*List\.of\(([^)]*)\)\s*\)", src, re.S):
        for n in re.findall(r"\"([^\"]+)\"", m.group(1)):
            inherited[n] = line_of(src, m.start())
    names = [d[0] for d in declared]
    dup = sorted({n for n in names if names.count(n) > 1})
    not_inh = [d for d in declared if d[0] not in inherited]
    inh_not_declared = sorted(n for n in inherited if n not in names)
    out.append("  declared options: %d (duplicates: %s)" % (len(declared), dup or "none"))
    out.append("  inherited as test-JVM system properties: %d of them (inherited names that are not declared here: %s)"
               % (len([d for d in declared if d[0] in inherited]), inh_not_declared or "none"))
    out.append("  declared and NOT inherited: %d" % len(not_inh))

    # readers of a name inside the test JVM: Java sources under lucene/ (not the build plugins)
    def readers(name):
        g = git(repo, "grep", "-n", "-F", '"%s"' % name, tree, "--", "lucene", ok=(0, 1)) or ""
        hits = []
        for ln in g.splitlines():
            parts = ln.split(":", 3)
            if len(parts) < 4 or not parts[1].endswith(".java"):
                continue
            hits.append("%s:%s: %s" % (parts[1], parts[2], parts[3].strip()[:110]))
        return hits

    out.append("")
    out.append("  -- the options that are declared and not inherited, with every Java line under lucene/ that names them")
    for name, kind, ln in not_inh:
        hs = readers(name)
        out.append("  %-34s %-7s plugin:%d   lines under lucene/: %d" % (name, kind, ln, len(hs)))
        for h in hs:
            out.append("        " + h)

    # test groups and their system properties
    out.append("")
    out.append("  -- test groups with a system property (@TestGroup(... sysProperty = ...)) and the build's handling")
    g = git(repo, "grep", "-n", "-E", r"@TestGroup\(.*sysProperty", tree, "--", "lucene", ok=(0, 1)) or ""
    consts = {}
    for ln in (git(repo, "grep", "-h", "-E", r"String SYSPROP_[A-Z_]+ = \"tests\.", tree, "--", "lucene", ok=(0, 1)) or "").splitlines():
        m = re.search(r"(SYSPROP_[A-Z_]+) = \"([^\"]+)\"", ln)
        if m:
            consts[m.group(1)] = m.group(2)
    for ln in g.splitlines():
        parts = ln.split(":", 3)
        m = re.search(r"sysProperty = ([A-Za-z_\.\"]+)", parts[3])
        key = m.group(1).strip('"') if m else "?"
        prop = consts.get(key, key)
        users = git(repo, "grep", "-c", "-E", r"^\s*@(LuceneTestCase\.)?%s\b" % group_annotation(repo, tree, parts[1], int(parts[2])),
                    tree, "--", "lucene", ok=(0, 1)) or ""
        n_users = sum(int(x.rsplit(":", 1)[1]) for x in users.splitlines() if x.rsplit(":", 1)[1].isdigit())
        out.append("  %-18s group at %s:%s   declared by the build: %-5s inherited: %-5s annotated uses under lucene/: %d"
                   % (prop, parts[1].split("/")[-1], parts[2], prop in names, prop in inherited, n_users))

    # reverse: tests.* names read as system properties under lucene/ and not forwarded by the plugin
    out.append("")
    out.append("  -- reverse: tests.* names that Java code under lucene/ reads as a system property, and the build's handling")
    g = git(repo, "grep", "-h", "-o", "-E",
            r"(getProperty|systemPropertyAs[A-Za-z]+|getBoolean|getInteger)\(\s*\"tests\.[A-Za-z0-9_.]+\"", tree, "--", "lucene", ok=(0, 1)) or ""
    read = sorted(set(re.findall(r"\"(tests\.[A-Za-z0-9_.]+)\"", g)) | set(consts.values()))
    for n in read:
        tag = "inherited" if n in inherited else ("declared, NOT inherited" if n in names else "not a declared option")
        out.append("  %-34s %s" % (n, tag))
    return out


def group_annotation(repo, tree, path, line):
    """Name of the annotation type declared right after a @TestGroup line."""
    src = git(repo, "show", "%s:%s" % (tree, path)) or ""
    lines = src.splitlines()
    for ln in lines[line:line + 6]:
        m = re.search(r"@interface\s+([A-Za-z]+)", ln)
        if m:
            return m.group(1)
    return "NoSuchAnnotation"


if __name__ == "__main__":
    repo = sys.argv[1]
    for t in sys.argv[2:]:
        print("\n".join(census(repo, t)))
