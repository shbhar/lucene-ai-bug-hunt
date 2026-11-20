#!/usr/bin/env python3
"""The tool on Lucene's real CHANGES.txt files (no synthetic text).

For each revision named, lucene/CHANGES.txt is written by `git show` into <scratch>/<name>/lucene/CHANGES.txt
(the "local clone" form of the tool's first two arguments). Three tables come out:

  sections.tsv      every release section of main and of branch_11x, with its size as the tool's own
                    extract_release_section returns it, against the two measured limits (4,096 and 131,071 bytes)
  tag-vs-main.tsv   for every release tag from 9.0.0 on: the release's section in the tag's file against the same
                    section in main's file: sizes, the lines of the true difference, and the lines the tool prints
                    as it is (under each interpreter given) and with the two flushes
  pairs.tsv         the comparisons of the branches as they are today

usage: real_sections.py --git REPO --scratch DIR --stock CHECKOUT --fixed CHECKOUT --out DIR
                        --main REV --branch11 REV --branch106 REV --python LABEL=EXE [--python LABEL=EXE ...]
"""

import argparse
import importlib.util
import json
import os
import re
import subprocess
import sys


def git_show(repo, rev, path):
  return subprocess.run(["git", "-C", repo, "show", "%s:%s" % (rev, path)], check=True, capture_output=True).stdout


def true_diff(tmp, a, b):
  pa, pb = os.path.join(tmp, "ref-a"), os.path.join(tmp, "ref-b")
  with open(pa, "wb") as f:
    f.write(a)
  with open(pb, "wb") as f:
    f.write(b)
  return subprocess.run(["diff", pa, pb], check=False, capture_output=True).stdout.decode("utf-8")


def run_tool(exe, tool, clone_a, clone_b, release):
  out = subprocess.run([exe, "-B", "-u", tool, clone_a, clone_b, release], check=True, capture_output=True).stdout.decode("utf-8")
  lines = out.split("\n")
  assert lines[0].startswith("NOTE: resolving") and lines[1].startswith("NOTE: resolving")
  rest = "\n".join(lines[2:])
  assert rest.endswith("\n")
  return rest[:-1]


def main():
  sys.dont_write_bytecode = True
  ap = argparse.ArgumentParser()
  for k in ("git", "scratch", "stock", "fixed", "out", "main", "branch11", "branch106"):
    ap.add_argument("--" + k, required=True)
  ap.add_argument("--python", action="append", required=True)
  a = ap.parse_args()
  os.makedirs(a.out, exist_ok=True)
  pythons = [p.split("=", 1) for p in a.python]
  stock = os.path.join(a.stock, "dev-tools", "scripts", "diff_lucene_changes.py")
  fixed = os.path.join(a.fixed, "dev-tools", "scripts", "diff_lucene_changes.py")
  spec = importlib.util.spec_from_file_location("stock_tool", stock)
  tool_mod = importlib.util.module_from_spec(spec)
  spec.loader.exec_module(tool_mod)

  tags = subprocess.run(["git", "-C", a.git, "tag", "-l", "releases/lucene/*"], check=True, capture_output=True, text=True).stdout.split()
  tags = [t for t in tags if re.fullmatch(r"releases/lucene/(9|10)\.\d+\.\d+", t)]
  tags.sort(key=lambda t: [int(x) for x in t.rsplit("/", 1)[1].split(".")])
  revs = {"main": a.main, "branch_11x": a.branch11, "branch_10_6": a.branch106}
  for t in tags:
    revs["tag-" + t.rsplit("/", 1)[1]] = t
  texts, clones = {}, {}
  for name, rev in revs.items():
    clones[name] = os.path.join(a.scratch, name)
    os.makedirs(os.path.join(clones[name], "lucene"), exist_ok=True)
    texts[name] = git_show(a.git, rev, "lucene/CHANGES.txt")
    with open(os.path.join(clones[name], "lucene", "CHANGES.txt"), "wb") as f:
      f.write(texts[name])

  def section(name, release):
    try:
      return tool_mod.extract_release_section(texts[name], release)
    except AssertionError:
      return None

  # 1. every section of main and of branch_11x
  small, mid, total = {}, {}, {}
  with open(os.path.join(a.out, "sections.tsv"), "w") as f:
    f.write("file\trelease\tbytes\tat_most_4096\tat_most_131071\n")
    for name in ("main", "branch_11x", "branch_10_6"):
      releases = re.findall(r"^=======+ Lucene (.*?) =======+$", texts[name].decode("utf-8"), re.MULTILINE)
      small[name] = mid[name] = total[name] = 0
      for rel in releases:
        s = section(name, rel)
        if s is None:  # the last section of the file: the tool's pattern needs a header after it
          f.write("%s\t%s\tno following header: the tool raises AssertionError\t\t\n" % (name, rel))
          continue
        total[name] += 1
        small[name] += len(s) <= 4096
        mid[name] += len(s) <= 131071
        f.write("%s\t%s\t%d\t%s\t%s\n" % (name, rel, len(s), len(s) <= 4096, len(s) <= 131071))

  # 2. each release's section: the tag's file against main's file
  tmp = a.scratch
  rows = []
  for t in tags:
    rel = t.rsplit("/", 1)[1]
    s_tag, s_main = section("tag-" + rel, rel), section("main", rel)
    if s_tag is None or s_main is None:
      rows.append({"release": rel, "note": "no such section in " + ("the tag's file" if s_tag is None else "main's file")})
      continue
    r = {"release": rel, "bugfix": not rel.endswith(".0"), "bytes_tag": len(s_tag), "bytes_main": len(s_main), "true_diff_lines": true_diff(tmp, s_tag, s_main).count("\n")}
    for label, exe in pythons:
      got = run_tool(exe, stock, clones["tag-" + rel], clones["main"], rel)
      r["stock_" + label + "_lines"] = got.count("\n")
      r["stock_" + label + "_is_true"] = got == true_diff(tmp, s_tag, s_main)
    got = run_tool(pythons[0][1], fixed, clones["tag-" + rel], clones["main"], rel)
    r["fixed_lines"] = got.count("\n")
    r["fixed_is_true"] = got == true_diff(tmp, s_tag, s_main)
    rows.append(r)
  cols = ["release", "bugfix", "bytes_tag", "bytes_main", "true_diff_lines"] + [k for label, _ in pythons for k in ("stock_%s_lines" % label, "stock_%s_is_true" % label)] + ["fixed_lines", "fixed_is_true"]
  with open(os.path.join(a.out, "tag-vs-main.tsv"), "w") as f:
    f.write("\t".join(cols) + "\n")
    for r in rows:
      f.write("\t".join(str(r.get(c, r.get("note", ""))) for c in cols) + "\n")

  # 3. the branches as they are today
  pairs = [("branch_11x", "main", "11.0.0"), ("branch_10_6", "main", "10.6.0"), ("branch_10_6", "branch_11x", "10.6.0"), ("tag-10.6.0", "main", "10.6.0"), ("tag-10.6.0", "branch_11x", "10.6.0"), ("tag-10.6.0", "branch_10_6", "10.6.0"), ("tag-10.5.2", "main", "10.5.2"), ("tag-10.5.2", "branch_11x", "10.5.2"), ("tag-10.5.2", "branch_10_6", "10.5.2")]
  prow = []
  for x, y, rel in pairs:
    sx, sy = section(x, rel), section(y, rel)
    td = true_diff(tmp, sx, sy)
    r = {"first": x, "second": y, "release": rel, "bytes_first": len(sx), "bytes_second": len(sy), "true_diff_lines": td.count("\n")}
    for label, exe in pythons:
      got = run_tool(exe, stock, clones[x], clones[y], rel)
      r["stock_" + label + "_lines"] = got.count("\n")
      r["stock_" + label + "_is_true"] = got == td
    got = run_tool(pythons[0][1], fixed, clones[x], clones[y], rel)
    r["fixed_lines"] = got.count("\n")
    r["fixed_is_true"] = got == td
    prow.append(r)
  pcols = ["first", "second", "release", "bytes_first", "bytes_second", "true_diff_lines"] + cols[5:]
  with open(os.path.join(a.out, "pairs.tsv"), "w") as f:
    f.write("\t".join(pcols) + "\n")
    for r in prow:
      f.write("\t".join(str(r[c]) for c in pcols) + "\n")

  done = [r for r in rows if "note" not in r]
  differ = [r for r in done if r["true_diff_lines"] > 0]
  summary = {
    "revisions": revs,
    "pythons": {label: subprocess.run([exe, "-c", "import sys;print(sys.version.split()[0])"], capture_output=True, text=True).stdout.strip() for label, exe in pythons},
    "sections_counted": total,
    "sections_of_at_most_4096_bytes": small,
    "sections_of_at_most_131071_bytes": mid,
    "release_tags_compared_with_main": len(done),
    "release_tags_skipped": [r for r in rows if "note" in r],
    "of_them_bugfix_releases": sum(1 for r in done if r["bugfix"]),
    "bugfix_releases_whose_section_is_at_most_4096_bytes_in_both_files": sum(1 for r in done if r["bugfix"] and max(r["bytes_tag"], r["bytes_main"]) <= 4096),
    "releases_whose_section_differs_between_tag_and_main": [r["release"] for r in differ],
    "fixed_tool_prints_something_else_than_the_true_difference": sum(1 for r in done + prow if not r["fixed_is_true"]),
  }
  for label, _ in pythons:
    summary["stock_%s: releases with a true difference where the tool prints nothing" % label] = [r["release"] for r in differ if r["stock_%s_lines" % label] == 0]
    summary["stock_%s: releases with a true difference where the tool prints something else than it" % label] = [r["release"] for r in differ if r["stock_%s_lines" % label] != 0 and not r["stock_%s_is_true" % label]]
    summary["stock_%s: releases where the tool prints the true difference" % label] = sum(1 for r in done if r["stock_%s_is_true" % label])
  with open(os.path.join(a.out, "summary.json"), "w") as f:
    json.dump(summary, f, indent=1)
    f.write("\n")
  print(json.dumps(summary, indent=1))


if __name__ == "__main__":
  main()
