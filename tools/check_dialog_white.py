# -*- coding: utf-8 -*-
"""🪟 containerColor 가 **진짜로** 빠진 AlertDialog 만 찾는다.

  `dup_guard` 의 `dialog-white` 규칙은 「`AlertDialog(` 뒤 2500자 안에 containerColor 가
  없으면」 이라는 **어림**이다. 그래서 인자가 긴 창은 **이미 흰색인데도 걸린다.**
  (2026-10-03: 그 규칙에 걸린 8곳 중 **진짜는 1곳**이었다 — StatsScreen 출발지 창)

  여기선 **괄호를 세서** 그 창의 인자 안만 본다. 고치기 전에 이걸 돌려라:

      python tools/check_dialog_white.py

  ⚠️ 가드 기준선에 남은 수는 「고칠 곳의 수」가 아니라 **「늘지만 마라」** 는 뜻이다.
"""
import glob
import io
import re


def args_span(s, start):
    """`AlertDialog(` 의 여는 괄호부터 짝이 맞는 닫는 괄호까지."""
    depth = 0
    i = start
    while i < len(s):
        if s[i] == "(":
            depth += 1
        elif s[i] == ")":
            depth -= 1
            if depth == 0:
                return s[start:i + 1]
        i += 1
    return s[start:start + 4000]


def main():
    total = 0
    pattern = re.compile("AlertDialog" + re.escape("("))
    for path in sorted(glob.glob(
            "app/src/main/java/com/detailline/callfollowcrm/presentation/**/*.kt", recursive=True)):
        shown = path.replace("\\", "/")
        if "/expo/" in shown:          # 박람회 = 별세계, 손대지 않는다
            continue
        src = io.open(path, encoding="utf-8").read()
        for m in pattern.finditer(src):
            line_start = src.rfind("\n", 0, m.start()) + 1
            if src[line_start:m.start()].lstrip().startswith("//"):
                continue               # 주석 안은 안 센다
            if "containerColor" not in args_span(src, m.end() - 1):
                line_no = src[:m.start()].count("\n") + 1
                print("  %-50s :%d" % (shown.split("/callfollowcrm/")[-1], line_no))
                total += 1
    print("containerColor 가 진짜로 빠진 곳: %d" % total)


if __name__ == "__main__":
    main()
