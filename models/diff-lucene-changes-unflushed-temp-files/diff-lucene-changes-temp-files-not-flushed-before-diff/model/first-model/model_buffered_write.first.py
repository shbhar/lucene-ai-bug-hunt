#!/usr/bin/env python3
"""Reference model of what `diff` reads in dev-tools/scripts/diff_lucene_changes.py, and a
differential run of the model and of the true difference against the tool itself.

MAPPING (model element -> source; script lines as of branch_11x 5d7ca54ba503 = main d7162870c1fd,
CPython lines as of the tags named)

  M1  buffer size B of a temporary file
        script :67   tempfile.NamedTemporaryFile()             (no buffering argument: -1)
        CPython Lib/tempfile.py NamedTemporaryFile -> _io.open(..., buffering=-1), mode "w+b"
        CPython Modules/_io/_iomodule.c _io_open_impl, "if (buffering < 0)":
          3.9 to 3.13:  B = raw._blksize   (st_blksize of the file when it is above 1, else DEFAULT_BUFFER_SIZE = 8 KiB)
          3.14, 3.15:   B = max(min(raw._blksize, 8 MiB), DEFAULT_BUFFER_SIZE = 128 KiB)
  M2  one write of n bytes into the empty buffer
        script :68-69  f1.write(branch1_changes); f2.write(branch2_changes)
        CPython Modules/_io/bufferedio.c _io_BufferedWriter_write_impl:
          n <= B: "Fast path: the data to write can be fully buffered" -> memcpy, nothing reaches the file
          n >  B: "while (remaining > self->buffer_size)" -> the raw write of all n bytes
        so the bytes of the file that another process can read are  visible(n) = 0 if n <= B else n
  M3  the comparison
        script :71-74  diff [extras] f1.name f2.name, run while both files are open and not flushed;
                       its exit status is not looked at (check=False), its stdout is printed
  M4  what the tool prints after its two NOTE lines
        predicted(s1, s2) = diff(visible(s1), visible(s2))
        true(s1, s2)      = diff(s1, s2)

The run (differential): for every pair of section sizes of a grid around B, two CHANGES.txt files are made whose
sections of the release 99.0.1 have exactly these sizes (as the tool's own extract_release_section returns them),
and three outputs are compared: the tool as it is, the tool with the two flushes, the true difference; the
model's prediction is compared with the tool as it is. Counters: cells whose true difference is not empty (the
check is not vacuous), cells where the tool as it is prints something else than the true difference, cells where
the model's prediction is not what the tool printed (0 = the model is the tool), cells where the repaired tool
prints something else than the true difference (0 = the two flushes are the whole repair).

usage: model_buffered_write.py --stock CHECKOUT --fixed CHECKOUT --out DIR --label TEXT
"""

import argparse
import importlib.util
import io
import json
import os
import subprocess
import sys
import tempfile

HEADER = "======================= Lucene {} ======================="
PREFIX = "\n\nBug Fixes\n---------------------\n"


def rule_buffer_size(blksize):
  """M1, from the CPython sources named above."""
  if sys.version_info >= (3, 14):
    return max(min(blksize, 8192 * 1024), io.DEFAULT_BUFFER_SIZE)
  return blksize if blksize > 1 else io.DEFAULT_BUFFER_SIZE


def visible_after_one_write(n):
  """Bytes of a NamedTemporaryFile that another process can read after one write of n bytes, before a flush."""
  with tempfile.NamedTemporaryFile() as f:
    f.write(b"x" * n)
    return os.stat(f.name).st_size


def measured_buffer_size():
  """The largest n with visible(n) == 0, by bisection over 1 .. 16 MiB."""
  lo, hi = 0, 16 * 1024 * 1024
  assert visible_after_one_write(hi) == hi
  while hi - lo > 1:
    mid = (lo + hi) // 2
    if visible_after_one_write(mid) == 0:
      lo = mid
    else:
      hi = mid
  return lo


def section(n, variant):
  """A release section of exactly n bytes (ASCII); the two variants of one size differ in one word."""
  s = PREFIX + "* GITHUB#1: %s fix. (Somebody)\n\n" % ("First" if variant == 0 else "Other")
  i = 2
  while True:
    e = "* GITHUB#%d: Fix number %d. (Somebody)\n\n" % (i, i)
    if len(s) + len(e) + 3 > n:
      break
    s += e
    i += 1
  k = n - len(s) - 3
  assert k >= 0, n
  s += "* " + "x" * k + "\n"
  assert len(s) == n
  return s


def changes_txt(sec):
  return HEADER.format("99.0.1") + sec + HEADER.format("99.0.0") + "\n"


def run_diff(tmp, a, b):
  pa, pb = os.path.join(tmp, "ref-a"), os.path.join(tmp, "ref-b")
  with open(pa, "wb") as f:
    f.write(a)
  with open(pb, "wb") as f:
    f.write(b)
  return subprocess.run(["diff", pa, pb], check=False, capture_output=True).stdout.decode("utf-8")


def run_tool(tool, clone_a, clone_b):
  out = subprocess.run([sys.executable, "-B", "-u", tool, clone_a, clone_b, "99.0.1"], check=True, capture_output=True).stdout.decode("utf-8")
  lines = out.split("\n")
  assert lines[0].startswith("NOTE: resolving") and lines[1].startswith("NOTE: resolving"), out[:200]
  rest = "\n".join(lines[2:])
  assert rest.endswith("\n")  # the newline that print() adds
  return rest[:-1]


def main():
  sys.dont_write_bytecode = True  # importing the tool must not leave a __pycache__ beside it
  ap = argparse.ArgumentParser()
  ap.add_argument("--stock", required=True)
  ap.add_argument("--fixed", required=True)
  ap.add_argument("--out", required=True)
  ap.add_argument("--label", required=True)
  a = ap.parse_args()
  os.makedirs(a.out, exist_ok=True)
  stock = os.path.join(a.stock, "dev-tools", "scripts", "diff_lucene_changes.py")
  fixed = os.path.join(a.fixed, "dev-tools", "scripts", "diff_lucene_changes.py")
  spec = importlib.util.spec_from_file_location("stock_tool", stock)
  stock_mod = importlib.util.module_from_spec(spec)
  spec.loader.exec_module(stock_mod)

  blksize = os.stat(tempfile.gettempdir()).st_blksize
  b_rule = rule_buffer_size(blksize)
  b_meas = measured_buffer_size()
  sizes = [100, 2000, b_meas - 1, b_meas, b_meas + 1, 3 * b_meas]
  cells = [(n1, n2, False) for n1 in sizes for n2 in sizes] + [(n, n, True) for n in sizes]

  rows = []
  with tempfile.TemporaryDirectory() as tmp:
    for idx, (n1, n2, same) in enumerate(cells):
      s1 = section(n1, 0).encode("utf-8")
      s2 = section(n2, 0 if same else 1).encode("utf-8")
      clones = []
      for name, s in (("a", s1), ("b", s2)):
        clone = os.path.join(tmp, "c%d-%s" % (idx, name))
        os.makedirs(os.path.join(clone, "lucene"))
        with open(os.path.join(clone, "lucene", "CHANGES.txt"), "wb") as f:
          f.write(changes_txt(s.decode("utf-8")).encode("utf-8"))
        assert stock_mod.extract_release_section(changes_txt(s.decode("utf-8")).encode("utf-8"), "99.0.1") == s
        clones.append(clone)
      true = run_diff(tmp, s1, s2)
      pred = run_diff(tmp, s1 if n1 > b_meas else b"", s2 if n2 > b_meas else b"")
      got_stock = run_tool(stock, clones[0], clones[1])
      got_fixed = run_tool(fixed, clones[0], clones[1])
      rows.append({
        "n1": n1, "n2": n2, "same_content": same,
        "true_diff_lines": true.count("\n"),
        "stock_lines": got_stock.count("\n"),
        "fixed_lines": got_fixed.count("\n"),
        "stock_is_true": got_stock == true,
        "stock_is_model": got_stock == pred,
        "fixed_is_true": got_fixed == true,
        "kind": ("equal sections" if same else "both in the buffer" if max(n1, n2) <= b_meas else "both written through" if min(n1, n2) > b_meas else "one in the buffer"),
      })

  differing = [r for r in rows if r["true_diff_lines"] > 0]
  summary = {
    "label": a.label,
    "python": sys.version.split()[0],
    "io_DEFAULT_BUFFER_SIZE": io.DEFAULT_BUFFER_SIZE,
    "tempdir": tempfile.gettempdir(),
    "tempdir_st_blksize": blksize,
    "buffer_size_by_the_rule_M1": b_rule,
    "buffer_size_measured": b_meas,
    "rule_is_measured": b_rule == b_meas,
    "sizes": sizes,
    "cells": len(rows),
    "cells_with_a_true_difference": len(differing),
    "stock_prints_something_else_than_the_true_difference": sum(1 for r in rows if not r["stock_is_true"]),
    "of_them_stock_prints_nothing": sum(1 for r in differing if r["stock_lines"] == 0),
    "of_them_stock_prints_a_whole_section": sum(1 for r in differing if not r["stock_is_true"] and r["stock_lines"] > 0),
    "model_prediction_differs_from_stock": sum(1 for r in rows if not r["stock_is_model"]),
    "fixed_prints_something_else_than_the_true_difference": sum(1 for r in rows if not r["fixed_is_true"]),
    "by_kind": {k: {"cells": sum(1 for r in rows if r["kind"] == k), "stock_wrong": sum(1 for r in rows if r["kind"] == k and not r["stock_is_true"])} for k in ("equal sections", "both in the buffer", "one in the buffer", "both written through")},
    "stock_tool": stock,
    "fixed_tool": fixed,
  }
  with open(os.path.join(a.out, "grid.tsv"), "w") as f:
    cols = ["n1", "n2", "kind", "true_diff_lines", "stock_lines", "fixed_lines", "stock_is_true", "stock_is_model", "fixed_is_true"]
    f.write("\t".join(cols) + "\n")
    for r in rows:
      f.write("\t".join(str(r[c]) for c in cols) + "\n")
  with open(os.path.join(a.out, "summary.json"), "w") as f:
    json.dump(summary, f, indent=1)
    f.write("\n")
  print(json.dumps(summary, indent=1))


if __name__ == "__main__":
  main()
