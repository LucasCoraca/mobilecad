// OpenGL ES 3 on Android, desktop OpenGL 3.3 core elsewhere; the shaders differ only in their #version line.
#pragma once
#ifdef __ANDROID__
#include <GLES3/gl3.h>
#define MCXR_GLSL_HEADER "#version 300 es\nprecision highp float;\n"
#else
#define GL_GLEXT_PROTOTYPES 1
#include <GL/gl.h>
#include <GL/glext.h>
#define MCXR_GLSL_HEADER "#version 330 core\n"
#endif
