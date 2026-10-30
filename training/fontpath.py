# -*- coding: utf-8 -*-
"""定位系统中文字体文件（PingFang 在 AssetsV2 里，路径随系统版本变化）。"""

import glob
import os

_CANDIDATES = [
    "/System/Library/AssetsV2/com_apple_MobileAsset_Font*/*/AssetData/PingFang.ttc",
    "/System/Library/Fonts/PingFang.ttc",
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/System/Library/Fonts/STHeiti Medium.ttc",
]
MONO_PATH = "/System/Library/Fonts/Menlo.ttc"

# PingFang.ttc face index: 3=SC Regular, 7=SC Medium, 11=SC Semibold
REG_INDEX = 3
BOLD_INDEX = 11


def cjk_path():
    for pat in _CANDIDATES:
        hits = sorted(glob.glob(pat))
        if hits:
            return hits[0]
    return None


def load(size, bold=False):
    from PIL import ImageFont
    p = cjk_path()
    idx = BOLD_INDEX if bold else REG_INDEX
    try:
        return ImageFont.truetype(p, size, index=idx)
    except Exception:
        return ImageFont.truetype(p, size, index=0)


def load_mono(size):
    from PIL import ImageFont
    try:
        return ImageFont.truetype(MONO_PATH, size, index=0)
    except Exception:
        return load(size)


if __name__ == "__main__":
    print(cjk_path())
