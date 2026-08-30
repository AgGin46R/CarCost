# -*- coding: utf-8 -*-
"""
Помечает @Composable функции, которые стали composable после выноса строк.

Функции-словари вида `fun FuelType.label(): String = when (this) { ... }`
возвращали русские надписи литералами. После выноса они зовут stringResource,
а это composable-функция — значит и сама функция должна быть composable.
Ошибка повторяется в каждом файле, где такой словарь есть, поэтому чинится
разбором вывода компилятора, а не руками.

Запуск: python fixcomposable.py < сохранённый вывод gradle
"""

import io
import os
import re
import sys

ERR = re.compile(
    r"^e: (?:file:///)?(?P<path>[^:]+(?::[^:]+)?):(?P<line>\d+):\d+ "
    r"Functions which invoke @Composable"
)


def main():
    targets = {}
    for raw in sys.stdin:
        m = ERR.match(raw.strip())
        if not m:
            continue
        path = m.group("path").replace("\\", "/")
        if path[1:3] == ":/" or path[1:3] == ":\\":
            pass
        targets.setdefault(path, set()).add(int(m.group("line")))

    if not targets:
        print("нечего чинить")
        return

    for path, lines in sorted(targets.items()):
        if not os.path.exists(path):
            print("нет файла:", path)
            continue
        src = io.open(path, encoding="utf-8").read().split("\n")
        for ln in sorted(lines, reverse=True):
            idx = ln - 1
            if idx < 0 or idx >= len(src):
                continue
            indent = re.match(r"[ \t]*", src[idx]).group(0)
            # Уже помечена — пропускаем
            if idx > 0 and "@Composable" in src[idx - 1]:
                continue
            src.insert(idx, indent + "@Composable")
        text = "\n".join(src)
        if "import androidx.compose.runtime.Composable" not in text and \
           "import androidx.compose.runtime.*" not in text:
            m = re.search(r"^import .*$", text, re.M)
            if m:
                text = (text[:m.start()] +
                        "import androidx.compose.runtime.Composable\n" +
                        text[m.start():])
        io.open(path + ".tmp", "w", encoding="utf-8", newline="\n").write(text)
        os.replace(path + ".tmp", path)
        print("%s: помечено %d" % (os.path.basename(path), len(lines)))


if __name__ == "__main__":
    main()
