// A tiny software canvas (RGBA, rounded rectangles, 8x16 bitmap text) for panels and labels drawn without a UI toolkit:
// the scale badge, and the desktop build's stand-in panel.
#pragma once
#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>
#include "font8x16.h"

namespace mcxr {

struct Canvas {
    int w, h; std::vector<uint8_t> px;
    Canvas(int w_, int h_) : w(w_), h(h_), px(w_ * h_ * 4, 0) {}
    void rect(int x0, int y0, int x1, int y1, uint8_t r, uint8_t g, uint8_t b, int radius = 0) {
        for (int y = std::max(0, y0); y < std::min(h, y1); ++y) for (int x = std::max(0, x0); x < std::min(w, x1); ++x) {
            int dx = std::max({0, x0 + radius - x, x - (x1 - 1 - radius)}), dy = std::max({0, y0 + radius - y, y - (y1 - 1 - radius)});
            if (dx * dx + dy * dy > radius * radius) continue;
            uint8_t* p = &px[(y * w + x) * 4]; p[0] = r; p[1] = g; p[2] = b; p[3] = 255;
        }
    }
    void text(int x0, int y0, const std::string& s, int scale, uint8_t r, uint8_t g, uint8_t b) {
        for (char c : s) {
            if (c < 32 || c > 126) c = '?';
            for (int gy = 0; gy < 16; ++gy) for (int gx = 0; gx < 8; ++gx) if (kFont8x16[c - 32][gy] & (0x80 >> gx))
                for (int sy = 0; sy < scale; ++sy) for (int sx = 0; sx < scale; ++sx) {
                    int x = x0 + gx * scale + sx, y = y0 + gy * scale + sy; if (x < 0 || y < 0 || x >= w || y >= h) continue;
                    uint8_t* p = &px[(y * w + x) * 4]; p[0] = r; p[1] = g; p[2] = b; p[3] = 255;
                }
            x0 += 8 * scale;
        }
    }
    static int textWidth(const std::string& s, int scale) { return (int)s.size() * 8 * scale; }
};

}  // namespace mcxr
