// The hand and controller models (glTF binary, from the WebXR input profiles: assets/models): a node tree of textured
// meshes whose nodes move with the controller's buttons, or a mesh skinned to the hand's joints.
#pragma once
#include <cstdint>
#include <functional>
#include <string>
#include <vector>
#include "renderer.h"
#include "xmath.h"

namespace mcxr {

class GltfModel {
public:
    /** Parses a .glb (no GL calls: those happen on the first draw). */
    bool load(const std::vector<uint8_t>& glb, std::string* error = nullptr);
    bool loaded() const { return !nodes.empty(); }

    /** A controller model at `pose` (its grip). Each "<name>_value" node is set between "<name>_min" and "<name>_max" by
     *  `value(name)` (0..1; the WebXR input profiles' visual responses: trigger, squeeze, thumbstick axes, buttons). */
    void draw(Renderer& r, const Pose& pose, const M4& vp, V3 eye, const std::function<float(const std::string&)>& value);

    /** A skinned hand: each joint (by its WebXR name) placed at a pose (world; -Z along the bone, +Y the back of the hand);
     *  `jointPose(name, &pose)` returns false for one it has none for. */
    void drawSkinned(Renderer& r, const M4& vp, V3 eye, const std::function<bool(const std::string&, Pose*)>& jointPose, V3 color, float alpha);

private:
    struct Node { std::string name; int parent = -1; V3 t; Q r; V3 s{1, 1, 1}; int mesh = -1; };
    struct Prim { std::vector<float> v; GpuMesh gpu; };       // x y z nx ny nz u v
    std::vector<Node> nodes;
    std::vector<std::vector<Prim>> meshes;
    std::vector<uint8_t> image; int imageW = 0, imageH = 0; GLuint tex = 0;
    V3 baseColor{1, 1, 1};
    // skin: joints (node indices), their inverse bind matrices, and per vertex of mesh 0 / prim 0: 4 joints + weights
    std::vector<int> skinJoints; std::vector<M4> inverseBind;
    std::vector<uint16_t> vJoints; std::vector<float> vWeights; std::vector<float> skinned; unsigned skinVersion = 0;
    std::vector<float> fade;                                   // per vertex of the skinned mesh: its opacity (the cut-off forearm fades)
    M4 local(int i, const std::vector<Node>& ns) const;
    M4 world(int i, const std::vector<Node>& ns) const;
    void prepare(Renderer& r);
};

}  // namespace mcxr
