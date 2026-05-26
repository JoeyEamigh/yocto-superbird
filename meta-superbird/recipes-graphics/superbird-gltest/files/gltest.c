// Surfaceless EGL + GLES2 test. Opens panfrosts render node, creates an EGL
// context via EGL_PLATFORM_SURFACELESS_MESA, clears to orange, reads one
// pixel back, prints GL_VENDOR/RENDERER/VERSION.
#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>

#define CHK(x) do { if (!(x)) { fprintf(stderr, "%s:%d fail: %s\n", __FILE__, __LINE__, #x); exit(1); } } while(0)

int main(void) {
    // Use EGL_PLATFORM_SURFACELESS_MESA — no window, no GBM.
    PFNEGLGETPLATFORMDISPLAYEXTPROC gpd = (PFNEGLGETPLATFORMDISPLAYEXTPROC)
        eglGetProcAddress("eglGetPlatformDisplayEXT");
    CHK(gpd);
    EGLDisplay dpy = gpd(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, NULL);
    CHK(dpy != EGL_NO_DISPLAY);

    EGLint major, minor;
    CHK(eglInitialize(dpy, &major, &minor));
    printf("EGL %d.%d\n", major, minor);
    printf("EGL_VENDOR:     %s\n", eglQueryString(dpy, EGL_VENDOR));
    printf("EGL_VERSION:    %s\n", eglQueryString(dpy, EGL_VERSION));
    printf("EGL_CLIENT_APIS:%s\n", eglQueryString(dpy, EGL_CLIENT_APIS));

    CHK(eglBindAPI(EGL_OPENGL_ES_API));
    EGLint cfg_attrs[] = { EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                            EGL_NONE };
    EGLConfig cfg; EGLint n = 0;
    CHK(eglChooseConfig(dpy, cfg_attrs, &cfg, 1, &n) && n == 1);

    EGLint ctx_attrs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctx_attrs);
    CHK(ctx != EGL_NO_CONTEXT);
    CHK(eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx));

    printf("GL_VENDOR:      %s\n", glGetString(GL_VENDOR));
    printf("GL_RENDERER:    %s\n", glGetString(GL_RENDERER));
    printf("GL_VERSION:     %s\n", glGetString(GL_VERSION));
    printf("GL_SHADING_LANGUAGE_VERSION: %s\n", glGetString(GL_SHADING_LANGUAGE_VERSION));
    // Render triangle into an FBO-backed texture
    const int W = 64, H = 64;
    GLuint tex, fbo;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    CHK(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);
    glViewport(0, 0, W, H);
    glClearColor(1.0f, 0.5f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // Draw one triangle with a simple shader
    const char *vs = "attribute vec2 p; void main(){gl_Position=vec4(p,0.0,1.0);}";
    const char *fs = "precision mediump float; void main(){gl_FragColor=vec4(0.0,0.8,1.0,1.0);}";
    GLuint vsh = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vsh, 1, &vs, NULL); glCompileShader(vsh);
    GLuint fsh = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fsh, 1, &fs, NULL); glCompileShader(fsh);
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vsh); glAttachShader(prog, fsh);
    glBindAttribLocation(prog, 0, "p");
    glLinkProgram(prog);
    GLint linked = 0; glGetProgramiv(prog, GL_LINK_STATUS, &linked);
    CHK(linked);
    glUseProgram(prog);
    float verts[] = { -0.9f,-0.9f, 0.9f,-0.9f, 0.0f, 0.9f };
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glFinish();

    unsigned char px[4];
    glReadPixels(W/2, H/2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
    printf("center pixel: RGBA = %u %u %u %u\n", px[0], px[1], px[2], px[3]);
    // Check corner (should be orange clear)
    glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
    printf("corner pixel: RGBA = %u %u %u %u\n", px[0], px[1], px[2], px[3]);
    // Error check
    GLenum e = glGetError();
    printf("glGetError at end: 0x%04x (0 == no error)\n", e);
    eglDestroyContext(dpy, ctx);
    eglTerminate(dpy);
    return e ? 1 : 0;
}
