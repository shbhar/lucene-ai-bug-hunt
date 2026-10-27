#!/usr/bin/env python3
"""Decode cvc5 (fp #b.. #b.. #b..) model values into Java bit patterns (hex) for replay."""
import re, sys
txt = open(sys.argv[1]).read()
for name, s, e, m in re.findall(r"\((\w+) \(fp #b([01]) #b([01]+) #b([01]+)\)\)", txt):
    bits = int(s + e + m, 2)
    kind = "double" if len(e) == 11 else "float"
    print(f"{name} {kind} {bits:#x}")
