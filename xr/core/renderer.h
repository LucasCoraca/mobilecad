// Draws the XR scene for one eye: the CAD model (lit faces, dark edges, hover / selection highlights on faces and edges),
// a floor grid, controller rays, cursor dots and textured panels (the app's UI, the info card). The same code runs on
// OpenGL ES 3 (headsets) and desktop GL 3.3 (simulator); on Android a panel can show an external (SurfaceTexture) image.
#pragma once
#include <cstdint>
#include <vector>
#include "gl.h"
#include "scene.h"
#include "xmath.h"

namespace mcxr {

/** A coloured line list: x y z r g b per vertex, two vertices per segment. */
struct Lines { std::vector<float> v; void add(V3 a, V3 b, V3 c) { for (V3 p : {a, b}) { v.insert(v.end(), {p.x, p.y, p.z, c.x, c.y, c.z}); } } };

/** Triangles on the GPU (x y z nx ny nz per vertex, then u v when textured), re-uploaded when `version` changes. */
struct GpuMesh { GLuint vao = 0, vbo = 0; int count = 0; unsigned version = ~0u; };

class Renderer {
public:
    bool init();
    void upload(Scene& s);                                   // (re)creates the mesh buffers when the scene changed
    void beginView(GLuint fbo, int x, int y, int w, int h, bool clear);
    /** `fade` < 1 draws the whole model see-through (the app dims it while a sketch is edited). */
    void drawScene(const Scene& s, const Pose& model, const M4& viewProj, V3 eyePos, ElemRef hover, const std::vector<ElemRef>& selected, float fade = 1.f);
    void drawLines(const Lines& l, const M4& viewProj);
    void uploadMesh(GpuMesh& m, const std::vector<float>& tris, unsigned version, int floatsPerVertex = 6);
    /** A lit mesh placed by `model`, its colour times texture `tex` if given (alpha < 1: blended, premultiplied). */
    void drawMesh(const GpuMesh& m, const M4& model, const M4& viewProj, V3 eye, V3 color, float alpha = 1.f, GLuint tex = 0);
    /** Points (x y z r g b; the second vertex of each Lines pair is ignored) drawn as round dots of `sizePx` pixels. */
    void drawDots(const std::vector<float>& pts, float sizePx, const M4& viewProj);
    /** A w x h rectangle centred on `center` (its XY plane), for the depth buffer: the caller masks the colour. */
    void drawRect(const Pose& center, float w, float h, const M4& viewProj);
    /** A textured rectangle centred on `center` (local X = width, local Y = height), corners rounded by `radius` (m). */
    /** `uv`: the part of the image shown (x y w h, 0..1, y down), whole by default; `alpha`: the image's own transparency
     *  (a window surface: premultiplied), else opaque. */
    void drawPanel(GLuint tex, bool external, const Pose& center, float w, float h, float radius, const M4& viewProj, float dim = 1.f,
                   const float* uv = nullptr, bool alpha = false, bool premultiplied = false);
    /** The image's alpha at w x h (row 0 = top), for hit testing what is drawn on a see-through surface. */
    void readAlpha(GLuint tex, bool external, int w, int h, std::vector<uint8_t>& out);
    GLuint makeTexture(int w, int h, const void* rgba, GLuint reuse = 0);      // mipmapped, trilinear + anisotropic
    /** The image copied into a mipmapped texture (premultiplied alpha kept) for sharp, shimmer-free sampling. */
    GLuint mipmappedCopy(GLuint src, bool external, int w, int h, GLuint reuse);
    /** The image drawn into texture `dst` (w x h, row 0 = bottom as OpenGL images are): for a compositor layer. Colours
     *  raised to `gammaOut` (2.2 for an sRGB target), alpha kept premultiplied, `holeUv` regions left transparent. */
    void drawImageInto(GLuint dst, int w, int h, GLuint src, bool external, float gammaOut, const std::vector<float>& holeUv);
    /** Forget GL objects of a previous context (they are gone with it): the next upload starts afresh. */
    void contextLost() { uploaded = ~0u; uploadedScene = nullptr; buffers.clear(); arrays.clear(); }
    float clear[3] = {0.11f, 0.13f, 0.16f};
    bool seeThrough = false;            // passthrough: clear to transparent, so the camera view shows where nothing is drawn
    /** Parts of the image drawPanel leaves out (up to 4, x0 y0 x1 y1 in 0..1, y down): shown elsewhere. */
    std::vector<float> holes;
    /** 2.2 when drawing into an sRGB swapchain (colours here are sRGB values, the target expects linear), 1 otherwise. */
    float gamma = 1.f;
    /** drawMesh multiplies the alpha by each vertex's u (a hand model fading out at the wrist). */
    bool fadeUv = false;
private:
    GLuint meshProg = 0, lineProg = 0, texProg = 0, oesProg = 0, lineVao = 0, lineVbo = 0, quadVao = 0, quadVbo = 0;
    GLint uMvp = -1, uModel = -1, uColor = -1, uLit = -1, uEye = -1;
    unsigned uploaded = ~0u; const Scene* uploadedScene = nullptr;
    GLuint maskFbo = 0, maskTex = 0; int maskW = 0, maskH = 0;
    GLuint copyFbo = 0; int copyW = 0, copyH = 0;
    std::vector<GLuint> buffers, arrays;                    // owned by the last upload
};

}  // namespace mcxr
