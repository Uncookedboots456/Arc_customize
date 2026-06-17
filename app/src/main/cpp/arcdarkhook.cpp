#include <android/asset_manager.h>
#include <android/log.h>
#include <jni.h>
#include <sys/stat.h>
#include <xhook.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>

#define LOG_TAG "ArcDarkNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr const char *kCocosPathRegex = ".*libcocos2dcpp\\.so$";
constexpr int kMaxHitLogs = 24;
constexpr int kMaxMissLogs = 120;

struct FakeAsset {
    FILE *file;
    off_t length;
    std::string asset_path;
};

std::mutex g_mutex;
std::unordered_map<std::string, std::string> g_overrides;
std::unordered_set<AAsset *> g_fake_assets;
int g_hit_logs = 0;
int g_miss_logs = 0;
bool g_registered = false;

AAsset *(*orig_AAssetManager_open)(AAssetManager *, const char *, int) = nullptr;
int (*orig_AAsset_read)(AAsset *, void *, size_t) = nullptr;
off_t (*orig_AAsset_getLength)(AAsset *) = nullptr;
void (*orig_AAsset_close)(AAsset *) = nullptr;

std::string normalize_path(const char *path) {
    if (path == nullptr) {
        return {};
    }

    std::string normalized(path);
    while (!normalized.empty() && normalized.front() == '/') {
        normalized.erase(normalized.begin());
    }
    return normalized;
}

std::string strip_assets_prefix(const std::string &path) {
    constexpr const char *prefix = "assets/";
    if (path.rfind(prefix, 0) == 0) {
        return path.substr(std::strlen(prefix));
    }
    return path;
}

bool lookup_override(const char *requested_path, std::string *asset_path, std::string *file_path) {
    std::string normalized = normalize_path(requested_path);
    if (normalized.empty()) {
        return false;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    auto found = g_overrides.find(normalized);
    if (found == g_overrides.end()) {
        found = g_overrides.find(strip_assets_prefix(normalized));
    }
    if (found == g_overrides.end()) {
        return false;
    }

    *asset_path = normalized;
    *file_path = found->second;
    return true;
}

FakeAsset *as_fake_asset(AAsset *asset) {
    if (asset == nullptr) {
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_fake_assets.find(asset) == g_fake_assets.end()) {
        return nullptr;
    }
    return reinterpret_cast<FakeAsset *>(asset);
}

AAsset *hook_AAssetManager_open(AAssetManager *manager, const char *filename, int mode) {
    std::string asset_path;
    std::string file_path;
    if (!lookup_override(filename, &asset_path, &file_path)) {
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            if (g_miss_logs < kMaxMissLogs) {
                g_miss_logs++;
                LOGI("asset miss #%d: %s",
                     g_miss_logs,
                     filename != nullptr ? filename : "(null)");
            }
        }
        return orig_AAssetManager_open(manager, filename, mode);
    }

    struct stat st {};
    if (stat(file_path.c_str(), &st) != 0) {
        LOGW("asset hit but stat failed: %s -> %s errno=%d", filename, file_path.c_str(), errno);
        return orig_AAssetManager_open(manager, filename, mode);
    }

    FILE *file = std::fopen(file_path.c_str(), "rb");
    if (file == nullptr) {
        LOGW("asset hit but fopen failed: %s -> %s errno=%d", filename, file_path.c_str(), errno);
        return orig_AAssetManager_open(manager, filename, mode);
    }

    auto *fake = new FakeAsset{file, static_cast<off_t>(st.st_size), asset_path};
    auto *asset = reinterpret_cast<AAsset *>(fake);
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_fake_assets.insert(asset);
        if (g_hit_logs < kMaxHitLogs) {
            g_hit_logs++;
            LOGI("asset hit #%d: %s -> %s (%lld bytes)",
                 g_hit_logs,
                 filename,
                 file_path.c_str(),
                 static_cast<long long>(st.st_size));
        }
    }
    return asset;
}

int hook_AAsset_read(AAsset *asset, void *buf, size_t count) {
    FakeAsset *fake = as_fake_asset(asset);
    if (fake == nullptr) {
        return orig_AAsset_read(asset, buf, count);
    }

    size_t read = std::fread(buf, 1, count, fake->file);
    if (read > static_cast<size_t>(std::numeric_limits<int>::max())) {
        return std::numeric_limits<int>::max();
    }
    return static_cast<int>(read);
}

off_t hook_AAsset_getLength(AAsset *asset) {
    FakeAsset *fake = as_fake_asset(asset);
    if (fake == nullptr) {
        return orig_AAsset_getLength(asset);
    }
    return fake->length;
}

void hook_AAsset_close(AAsset *asset) {
    FakeAsset *fake = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto found = g_fake_assets.find(asset);
        if (found != g_fake_assets.end()) {
            fake = reinterpret_cast<FakeAsset *>(asset);
            g_fake_assets.erase(found);
        }
    }

    if (fake == nullptr) {
        orig_AAsset_close(asset);
        return;
    }

    std::fclose(fake->file);
    delete fake;
}

jint register_hooks_once() {
    if (g_registered) {
        return 1;
    }

    xhook_enable_debug(0);
    xhook_enable_sigsegv_protection(1);

    int open_result = xhook_register(
            kCocosPathRegex,
            "AAssetManager_open",
            reinterpret_cast<void *>(hook_AAssetManager_open),
            reinterpret_cast<void **>(&orig_AAssetManager_open));
    int read_result = xhook_register(
            kCocosPathRegex,
            "AAsset_read",
            reinterpret_cast<void *>(hook_AAsset_read),
            reinterpret_cast<void **>(&orig_AAsset_read));
    int length_result = xhook_register(
            kCocosPathRegex,
            "AAsset_getLength",
            reinterpret_cast<void *>(hook_AAsset_getLength),
            reinterpret_cast<void **>(&orig_AAsset_getLength));
    int close_result = xhook_register(
            kCocosPathRegex,
            "AAsset_close",
            reinterpret_cast<void *>(hook_AAsset_close),
            reinterpret_cast<void **>(&orig_AAsset_close));

    LOGI("xhook register results open=%d read=%d length=%d close=%d",
         open_result,
         read_result,
         length_result,
         close_result);
    if (open_result != 0 || read_result != 0 || length_result != 0 || close_result != 0) {
        LOGE("xhook register failed");
        return 0;
    }
    g_registered = true;
    return 1;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_dev_arc_assets_NativeBridge_nativeInstall(
        JNIEnv *env,
        jclass,
        jobjectArray asset_paths,
        jobjectArray file_paths) {
    jsize asset_count = env->GetArrayLength(asset_paths);
    jsize file_count = env->GetArrayLength(file_paths);
    if (asset_count != file_count) {
        LOGE("nativeInstall array length mismatch assets=%d files=%d", asset_count, file_count);
        return 0;
    }

    std::unordered_map<std::string, std::string> overrides;
    overrides.reserve(static_cast<size_t>(asset_count) * 2);

    for (jsize i = 0; i < asset_count; i++) {
        auto asset_string = static_cast<jstring>(env->GetObjectArrayElement(asset_paths, i));
        auto file_string = static_cast<jstring>(env->GetObjectArrayElement(file_paths, i));
        if (asset_string == nullptr || file_string == nullptr) {
            LOGE("nativeInstall received null path at index=%d", i);
            return 0;
        }
        const char *asset_chars = env->GetStringUTFChars(asset_string, nullptr);
        const char *file_chars = env->GetStringUTFChars(file_string, nullptr);
        if (asset_chars == nullptr || file_chars == nullptr) {
            LOGE("nativeInstall failed to read path at index=%d", i);
            if (asset_chars != nullptr) {
                env->ReleaseStringUTFChars(asset_string, asset_chars);
            }
            if (file_chars != nullptr) {
                env->ReleaseStringUTFChars(file_string, file_chars);
            }
            env->DeleteLocalRef(asset_string);
            env->DeleteLocalRef(file_string);
            return 0;
        }

        std::string asset_path = normalize_path(asset_chars);
        std::string file_path(file_chars);
        overrides[asset_path] = file_path;
        overrides[strip_assets_prefix(asset_path)] = file_path;

        env->ReleaseStringUTFChars(asset_string, asset_chars);
        env->ReleaseStringUTFChars(file_string, file_chars);
        env->DeleteLocalRef(asset_string);
        env->DeleteLocalRef(file_string);
    }

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_overrides.swap(overrides);
        g_hit_logs = 0;
        g_miss_logs = 0;
    }

    if (register_hooks_once() != 1) {
        return 0;
    }
    int refresh_result = xhook_refresh(0);
    LOGI("nativeInstall mapped %d assets (%zu lookup keys), refresh=%d",
         asset_count,
         g_overrides.size(),
         refresh_result);
    return 1;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_arc_assets_NativeBridge_nativeRefreshHooks(JNIEnv *, jclass) {
    if (register_hooks_once() != 1) {
        return -1;
    }
    return xhook_refresh(0);
}
