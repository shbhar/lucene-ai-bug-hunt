"""Static model of HTMLStripCharFilter's end-of-input policy versus its mid-text failure policy.

Model element -> source (HTMLStripCharFilter.jflex @ a9779ef3c193):
  STATES          %xstate declarations, lines 44-50
  EOF_ACTION[q]   the %eof switch, lines 275-323: groups "Exclude" (284-296), "Substitute" (297-305),
                  "Include" (306-318), default (319-321)
  OWN_CATCHALL[q] a state's own one-char rule '[^] {...}' that consumes the char and stays in a state that keeps
                  accumulating (BANG 782, CDATA 795, COMMENT 814, SERVER_SIDE_INCLUDE 829, SCRIPT_COMMENT 837,
                  STYLE_COMMENT 845, SINGLE/DOUBLE_QUOTED_STRING 849-857, SCRIPT 884, STYLE 911)
  RESTORE[q]      membership of the shared fallback block, lines 914-922: when no rule of the state matches the
                  next char, the pending input segment is pushed back and output verbatim ("Include")

Obligation (the property the unit checks, "end of input behaves like mid-text"):
  for every state q in which a construct is still pending and whose mid-text failure is RESTORE (the pending
  input becomes visible text), the end-of-input action must also be Include.  A state whose own catch-all keeps
  consuming (comments, script, quoted strings) is consistent with any EOF action that drops the pending input,
  because mid-text that input is also never output.

The script parses the real .jflex (no transcription by hand), prints the table and the violating states.
"""
import re
import sys

src = open(sys.argv[1], encoding="utf-8").read()

states = []
for m in re.finditer(r"^%xstate\s+(.*)$", src, re.M):
    states += [s.strip() for s in m.group(1).split(",") if s.strip()]

eof = re.search(r"%eof\{(.*?)%eof\}", src, re.S).group(1)
eof_action = {}
pending = []
for line in eof.splitlines():
    m = re.match(r"\s*case\s+(\w+)\s*:\s*(\{\s*//\s*(\w+))?", line)
    if m:
        pending.append(m.group(1))
        if m.group(3):
            for s in pending:
                eof_action[s] = m.group(3)
            pending = []
for s in states:
    eof_action.setdefault(s, "default(-1)")

# shared fallback block: <A,B,...> { [^] { yypushback(...); outputSegment = inputSegment; ... } }
restore = set()
for m in re.finditer(r"^<([A-Z_,]+)>\s*\{\s*\n\s*\[\^\]\s*\{\s*\n\s*yypushback\(yylength\(\)\);\s*\n\s*outputSegment = inputSegment;",
                     src, re.M):
    restore |= set(m.group(1).split(","))

# a state's own catch-all: a block "<STATE> {" containing a line that starts with "[^]" (before the shared block)
own_catchall = set()
for m in re.finditer(r"^<([A-Z_]+)>\s*\{(.*?)^\}", src, re.M | re.S):
    if re.search(r"^\s*\[\^\]\s*\{", m.group(2), re.M):
        own_catchall.add(m.group(1))

print("%-26s %-12s %-14s %s" % ("state", "EOF action", "mid-text fail", "consistent?"))
viol = []
for s in states:
    if s in own_catchall:
        mid = "consume"           # own [^] rule wins (it is listed earlier than the shared block)
    elif s in restore:
        mid = "restore"
    else:
        mid = "n/a"
    ok = True
    if mid == "restore" and eof_action[s] != "Include":
        ok = False
        viol.append(s)
    print("%-26s %-12s %-14s %s" % (s, eof_action[s], mid, "yes" if ok else "NO"))
print()
print("states whose end-of-input action differs from their mid-text failure action:", viol)
print("states checked:", len(states))
