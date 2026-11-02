"""Generate the original Chat Chronicle icon using only Python's standard library."""
from pathlib import Path
import math
import struct
import zlib

S = 2
W = 512 * S
pixels = bytearray(W * W * 4)


def paint(bounds, color, contains):
    for y in range(max(0, int(bounds[1] * S)), min(W, int(bounds[3] * S))):
        for x in range(max(0, int(bounds[0] * S)), min(W, int(bounds[2] * S))):
            if contains((x + .5) / S, (y + .5) / S):
                off = (y * W + x) * 4
                pixels[off:off + 4] = bytes(color)


def rounded(box, radius, color):
    x0, y0, x1, y1 = box
    paint(box, color, lambda x, y: (x - min(max(x, x0 + radius), x1 - radius)) ** 2
          + (y - min(max(y, y0 + radius), y1 - radius)) ** 2 <= radius ** 2)


def circle(x, y, radius, color):
    paint((x - radius, y - radius, x + radius, y + radius), color,
          lambda px, py: (px - x) ** 2 + (py - y) ** 2 <= radius ** 2)


navy = (13, 27, 42, 255)
white = (230, 245, 250, 255)
cyan = (82, 212, 218, 255)
gold = (255, 184, 77, 255)
rounded((0, 0, 512, 512), 108, navy)
rounded((70, 90, 432, 362), 46, white)
paint((112, 330, 212, 422), white, lambda x, y: x <= 212 - (y - 330))
rounded((86, 106, 416, 346), 32, (22, 49, 66, 255))
paint((128, 326, 184, 385), (22, 49, 66, 255), lambda x, y: x <= 184 - (y - 326))
rounded((126, 152, 362, 174), 11, cyan)
rounded((126, 212, 324, 234), 11, cyan)
rounded((126, 272, 264, 294), 11, cyan)
circle(376, 358, 96, navy)
circle(376, 358, 80, gold)
rounded((367, 308, 385, 367), 9, navy)
paint((368, 350, 415, 391), navy,
      lambda x, y: abs((y - 358) - .67 * (x - 376)) < 10 and x >= 370)
circle(376, 358, 12, navy)

rows = bytearray()
for y in range(512):
    rows.append(0)
    for x in range(512):
        for channel in range(4):
            total = sum(pixels[((y * S + dy) * W + x * S + dx) * 4 + channel]
                        for dy in range(S) for dx in range(S))
            rows.append(round(total / (S * S)))


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 512, 512, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b'')
path = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/chatchronicle/icon.png'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_bytes(png)
print(path)
