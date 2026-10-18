// JNI bridge: embeds CPython and imports FreeCAD's App layer (FreeCAD.so) + the mobilecad.bridge Python module.
// All calls must come from one engine thread (Kotlin side guarantees it).
#include <jni.h>
#include <Python.h>
#include <android/log.h>
#include <cstdlib>
#include <dlfcn.h>
#include <fstream>
#include <string>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "fcbridge", __VA_ARGS__)

#include <pthread.h>
#include <unistd.h>

// stdout/stderr -> logcat (Python and FreeCAD print diagnostics there)
static int g_pipe[2];
static void* logThread(void*) {
    char buf[1024]; std::string line; ssize_t n;
    while ((n = read(g_pipe[0], buf, sizeof buf)) > 0) {
        for (ssize_t i = 0; i < n; i++) { if (buf[i] == '\n') { __android_log_write(ANDROID_LOG_INFO, "freecad", line.c_str()); line.clear(); } else line += buf[i]; }
    }
    return nullptr;
}
static void redirectStdio() {
    static bool done = false; if (done) return; done = true;
    setvbuf(stdout, nullptr, _IOLBF, 0); setvbuf(stderr, nullptr, _IONBF, 0);
    pipe(g_pipe); dup2(g_pipe[1], 1); dup2(g_pipe[1], 2);
    pthread_t t; pthread_create(&t, nullptr, logThread, nullptr); pthread_detach(t);
}
static void onExit() { __android_log_write(ANDROID_LOG_ERROR, "fcbridge", "process exit() called"); fflush(stdout); fflush(stderr); usleep(200000); }

static PyObject* g_handle = nullptr;     // mobilecad.bridge.handle
static PyObject* g_tess = nullptr;       // mobilecad.bridge.tessellate

static std::string pyErr() {
    PyObject *t, *v, *tb; PyErr_Fetch(&t, &v, &tb); PyErr_NormalizeException(&t, &v, &tb);
    std::string msg = "python error";
    PyObject* tbmod = PyImport_ImportModule("traceback");
    if (tbmod) {
        PyObject* lines = PyObject_CallMethod(tbmod, "format_exception", "OOO", t ? t : Py_None, v ? v : Py_None, tb ? tb : Py_None);
        if (lines) { PyObject* sep = PyUnicode_FromString(""); PyObject* j = PyUnicode_Join(sep, lines);
            if (j) { msg = PyUnicode_AsUTF8(j); Py_DECREF(j); } Py_DECREF(sep); Py_DECREF(lines); }
        Py_DECREF(tbmod);
    }
    Py_XDECREF(t); Py_XDECREF(v); Py_XDECREF(tb); PyErr_Clear(); return msg;
}

static std::string jstr(JNIEnv* env, jstring s) { const char* c = env->GetStringUTFChars(s, nullptr); std::string r(c); env->ReleaseStringUTFChars(s, c); return r; }

extern "C" JNIEXPORT jstring JNICALL
Java_app_mobilecad_freecad_Engine_nativeInit(JNIEnv* env, jobject, jstring jpyHome, jstring jfcHome, jstring juser) {
    std::string pyHome = jstr(env, jpyHome), fcHome = jstr(env, jfcHome), user = jstr(env, juser);
    redirectStdio(); atexit(onExit);
    // Preload FreeCAD + dependency libraries (dependency order) so DT_NEEDED entries resolve by soname.
    {
        std::ifstream in(fcHome + "/preload.txt"); std::string name; int ok = 0, bad = 0;
        while (std::getline(in, name)) {
            if (name.empty()) continue;
            if (dlopen((fcHome + "/lib/" + name).c_str(), RTLD_NOW | RTLD_GLOBAL)) ok++;
            else { bad++; LOG("preload %s: %s", name.c_str(), dlerror()); }
        }
        LOG("preloaded %d libraries (%d failed)", ok, bad);
    }
    // QtCore's Android backend needs its JNI_OnLoad (normally run by System.loadLibrary in a QtActivity)
    {
        const char* abiSuffix = sizeof(void*) == 8 ? (
#if defined(__x86_64__)
            "x86_64"
#else
            "arm64-v8a"
#endif
        ) : "";
        std::string qt = fcHome + "/lib/libQt6Core_" + abiSuffix + ".so";
        void* h = dlopen(qt.c_str(), RTLD_NOW | RTLD_NOLOAD);
        auto onLoad = h ? reinterpret_cast<jint (*)(JavaVM*, void*)>(dlsym(h, "JNI_OnLoad")) : nullptr;
        JavaVM* vm = nullptr; env->GetJavaVM(&vm);
        jint r = onLoad ? onLoad(vm, nullptr) : -1;
        LOG("QtCore JNI_OnLoad -> %d", (int)r);
    }
    setenv("FREECAD_USER_HOME", user.c_str(), 1);
    setenv("HOME", user.c_str(), 1);
    setenv("TMPDIR", (user + "/tmp").c_str(), 1);
    PyConfig config; PyConfig_InitPythonConfig(&config);
    config.install_signal_handlers = 0; config.write_bytecode = 0;
    PyStatus st = PyConfig_SetBytesString(&config, &config.home, pyHome.c_str());
    if (PyStatus_Exception(st)) { PyConfig_Clear(&config); return env->NewStringUTF("PyConfig home failed"); }
    LOG("Py_InitializeFromConfig home=%s", pyHome.c_str());
    st = Py_InitializeFromConfig(&config); PyConfig_Clear(&config);
    LOG("python initialised");
    if (PyStatus_Exception(st)) return env->NewStringUTF(st.err_msg ? st.err_msg : "Py_Initialize failed");
    std::string boot =
        "import sys, os\n"
        "fc = r'" + fcHome + "'\n"
        "for p in (fc + '/lib', fc + '/bridge', fc + '/Mod', fc + '/Ext'):\n"
        "    if p not in sys.path: sys.path.insert(0, p)\n"
        "import FreeCAD\n"
        "import mobilecad.bridge\n";
    // run step by step so a failure is attributable
    std::string pre = "import sys, os\nfc = r'" + fcHome + "'\nfor p in (fc + '/lib', fc + '/bridge', fc + '/Mod', fc + '/Ext'):\n    sys.path.insert(0, p)\n";
    if (PyRun_SimpleString(pre.c_str()) != 0) return env->NewStringUTF("sys.path setup failed");
    LOG("importing FreeCAD");
    if (PyRun_SimpleString("import FreeCAD") != 0) { fflush(stderr); return env->NewStringUTF("import FreeCAD failed (see logcat tag freecad)"); }
    LOG("FreeCAD imported");
    if (PyRun_SimpleString("import mobilecad.bridge") != 0) { fflush(stderr); return env->NewStringUTF("import mobilecad.bridge failed (see logcat tag freecad)"); }
    PyObject* mod = PyImport_ImportModule("mobilecad.bridge");
    if (!mod) return env->NewStringUTF(pyErr().c_str());
    g_handle = PyObject_GetAttrString(mod, "handle"); g_tess = PyObject_GetAttrString(mod, "tessellate"); Py_DECREF(mod);
    PyEval_SaveThread();     // release the GIL; every call re-acquires it
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_mobilecad_freecad_Engine_nativeCall(JNIEnv* env, jobject, jstring jreq) {
    std::string req = jstr(env, jreq), out;
    PyGILState_STATE g = PyGILState_Ensure();
    PyObject* r = PyObject_CallFunction(g_handle, "s", req.c_str());
    if (r) { out = PyUnicode_AsUTF8(r); Py_DECREF(r); }
    else {
        std::string e = pyErr(), esc; LOG("%s", e.c_str());
        for (char c : e) { if (c == '"' || c == '\\') esc += '\\'; if (c == '\n') { esc += "\\n"; continue; } esc += c; }
        out = "{\"ok\":false,\"error\":\"" + esc + "\"}";
    }
    PyGILState_Release(g);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_mobilecad_freecad_Engine_nativeTessellate(JNIEnv* env, jobject, jstring jname, jdouble tol) {
    std::string name = jstr(env, jname); jbyteArray arr = nullptr;
    PyGILState_STATE g = PyGILState_Ensure();
    PyObject* r = PyObject_CallFunction(g_tess, "sd", name.c_str(), (double)tol);
    if (r && PyBytes_Check(r)) {
        Py_ssize_t n = PyBytes_Size(r); arr = env->NewByteArray((jsize)n);
        env->SetByteArrayRegion(arr, 0, (jsize)n, reinterpret_cast<const jbyte*>(PyBytes_AsString(r)));
    } else LOG("tessellate %s failed: %s", name.c_str(), pyErr().c_str());
    Py_XDECREF(r); PyGILState_Release(g);
    return arr ? arr : env->NewByteArray(0);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_mobilecad_freecad_Engine_nativeExec(JNIEnv* env, jobject, jstring jsrc) {
    std::string src = jstr(env, jsrc), out;
    PyGILState_STATE g = PyGILState_Ensure();
    if (PyRun_SimpleString(src.c_str()) != 0) out = "error";
    PyGILState_Release(g);
    return env->NewStringUTF(out.c_str());
}
