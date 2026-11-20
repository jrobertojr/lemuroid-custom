// Local, headless Libretro comparison. All writes go to an isolated test directory.
#include "../src/main/cpp/upstream/libretro/libretro.h"
#include <algorithm>
#include <chrono>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <map>
#include <string>
#include <vector>
#include <sys/stat.h>

static std::map<std::string, std::string> options;
static std::string systemDir, saveDir;
static int videoFrames;
static size_t audioFrames, audibleSamples;
static int currentFrame, pixelFormat = RETRO_PIXEL_FORMAT_0RGB1555;
static const void* lastVideo;
static unsigned lastWidth, lastHeight;
static size_t lastPitch;
static void logger(retro_log_level, const char* fmt, ...) {
    va_list args; va_start(args, fmt); vfprintf(stderr, fmt, args); va_end(args);
}
static bool environment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char**>(data) = systemDir.c_str(); return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char**>(data) = saveDir.c_str(); return true;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            static_cast<retro_log_callback*>(data)->log = logger; return true;
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            auto vars = static_cast<retro_variable*>(data);
            for (; vars->key; ++vars) {
                if (options.count(vars->key)) continue;
                std::string values = vars->value ? vars->value : "";
                const auto start = values.find("; ");
                if (start != std::string::npos) values = values.substr(start + 2);
                options[vars->key] = values.substr(0, values.find('|'));
            }
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *static_cast<unsigned*>(data) = 0; return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto var = static_cast<retro_variable*>(data);
            auto found = options.find(var->key);
            var->value = found == options.end() ? nullptr : found->second.c_str();
            return found != options.end();
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *static_cast<bool*>(data) = false; return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            pixelFormat = *static_cast<int*>(data); return true;
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
            return true;
        default: return false;
    }
}
static void video(const void* data, unsigned w, unsigned h, size_t pitch) {
    if (data) { ++videoFrames; lastVideo = data; lastWidth = w; lastHeight = h; lastPitch = pitch; }
}
static size_t audio(const int16_t* data, size_t frames) {
    audioFrames += frames;
    for (size_t i = 0; i < frames * 2; ++i) if (data[i]) ++audibleSamples;
    return frames;
}
static void audioSingle(int16_t l, int16_t r) { const int16_t data[] = {l, r}; audio(data, 1); }
static void poll() {}
static int16_t input(unsigned, unsigned device, unsigned, unsigned id) {
    if (device != RETRO_DEVICE_JOYPAD) return 0;
    if (id == RETRO_DEVICE_ID_JOYPAD_START && currentFrame >= 480 && currentFrame < 485) return 1;
    if (id == RETRO_DEVICE_ID_JOYPAD_A &&
        ((currentFrame >= 600 && currentFrame < 605) || (currentFrame >= 660 && currentFrame < 665))) return 1;
    return 0;
}
template <typename T> static T symbol(void* lib, const char* name) {
    auto ptr = reinterpret_cast<T>(dlsym(lib, name));
    if (!ptr) { fprintf(stderr, "Missing symbol: %s\n", name); std::exit(3); }
    return ptr;
}

int main(int argc, char** argv) {
    if (argc < 7) { fprintf(stderr, "core.so rom.nds system-dir isolated-save-dir noods|desmume|desmume2015 quality|balanced|performance|reference-no-skip [frames]\n"); return 2; }
    systemDir = argv[3]; saveDir = argv[4]; mkdir(saveDir.c_str(), 0700);
    const std::string coreName = argv[5], profile = argv[6];
    const bool quality = profile == "quality", performance = profile == "performance";
    if (coreName == "dsperate") {
        options = {{"dsperate_jit", "enabled"}, {"dsperate_frameskip", "disabled"},
            {"dsperate_slot2", "empty"}, {"dsperate_screen_layout", "top"}};
    } else if (coreName == "noods") {
        options = {{"noods_directBoot", "enabled"}, {"noods_fpsLimiter", "disabled"},
            {"noods_frameskip", "disabled"}, {"noods_romInRam", "disabled"},
            {"noods_threaded2D", "enabled"}, {"noods_threaded3D", "2 Threads"},
            {"noods_arm7Hle", "disabled"},
            {"noods_highRes3D", quality ? "enabled" : "disabled"},
            {"noods_screenFilter", quality ? "Linear" : "Nearest"},
            {"noods_screenGhost", "disabled"}, {"noods_screenLayout", "top"},
            {"noods_emulateAudio", "enabled"}, {"noods_audio16Bit", "enabled"}};
    } else {
        options = {{"desmume_cpu_mode", "jit"}, {"desmume_jit_block_size", "12"},
            {"desmume_num_cores", "2"}, {"desmume_internal_resolution", "256x192"},
            {"desmume_screens_layout", "top only"}, {"desmume_frameskip", performance ? "2" : "0"}};
        if (performance) {
            options["desmume_gfx_edgemark"] = "disabled";
            options["desmume_gfx_linehack"] = "disabled";
            options["desmume_gfx_txthack"] = "disabled";
        }
        if (coreName == "desmume2015") {
            if (profile == "balanced") options["desmume_frameskip"] = "1";
            std::map<std::string, std::string> isolated;
            for (const auto& entry : options)
                isolated["desmume2015_" + entry.first.substr(8)] = entry.second;
            options.swap(isolated);
        }
    }
    void* lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!lib) { fprintf(stderr, "%s\n", dlerror()); return 3; }
    symbol<void(*)(retro_environment_t)>(lib, "retro_set_environment")(environment);
    symbol<void(*)(retro_video_refresh_t)>(lib, "retro_set_video_refresh")(video);
    symbol<void(*)(retro_audio_sample_batch_t)>(lib, "retro_set_audio_sample_batch")(audio);
    symbol<void(*)(retro_audio_sample_t)>(lib, "retro_set_audio_sample")(audioSingle);
    symbol<void(*)(retro_input_poll_t)>(lib, "retro_set_input_poll")(poll);
    symbol<void(*)(retro_input_state_t)>(lib, "retro_set_input_state")(input);
    symbol<void(*)()>(lib, "retro_init")();
    retro_game_info game{}; game.path = argv[2];
    if (!symbol<bool(*)(const retro_game_info*)>(lib, "retro_load_game")(&game)) { fprintf(stderr, "Load failed\n"); return 4; }
    auto run = symbol<void(*)()>(lib, "retro_run");
    retro_system_av_info av{};
    symbol<void(*)(retro_system_av_info*)>(lib, "retro_get_system_av_info")(&av);
    for (currentFrame = 0; currentFrame < 720; ++currentFrame) run();
    videoFrames = 0; audioFrames = 0; audibleSamples = 0;
    const int frames = argc > 7 ? std::max(1, std::atoi(argv[7])) : 600;
    const auto start = std::chrono::steady_clock::now();
    for (int i = 0; i < frames; ++i, ++currentFrame) run();
    const double seconds = std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();
    const int measuredVideo = videoFrames;
    const size_t measuredAudio = audioFrames, measuredAudible = audibleSamples;
    if (lastVideo) {
        FILE* image = fopen((saveDir + "/last-frame.ppm").c_str(), "wb");
        if (image) {
            fprintf(image, "P6\n%u %u\n255\n", lastWidth, lastHeight);
            for (unsigned y = 0; y < lastHeight; ++y) for (unsigned x = 0; x < lastWidth; ++x) {
                const auto row = static_cast<const uint8_t*>(lastVideo) + y * lastPitch;
                uint8_t rgb[3];
                if (pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
                    const uint32_t p = reinterpret_cast<const uint32_t*>(row)[x];
                    rgb[0] = p >> 16; rgb[1] = p >> 8; rgb[2] = p;
                } else {
                    const uint16_t p = reinterpret_cast<const uint16_t*>(row)[x];
                    const bool is565 = pixelFormat == RETRO_PIXEL_FORMAT_RGB565;
                    rgb[0] = ((p >> (is565 ? 11 : 10)) & 31) * 255 / 31;
                    rgb[1] = ((p >> 5) & (is565 ? 63 : 31)) * 255 / (is565 ? 63 : 31);
                    rgb[2] = (p & 31) * 255 / 31;
                }
                fwrite(rgb, 1, 3, image);
            }
            fclose(image);
        }
    }
    const auto stateSize = symbol<size_t(*)()>(lib, "retro_serialize_size")();
    bool stateSaved = false, stateRestored = false;
    auto memoryData = symbol<void*(*)(unsigned)>(lib, "retro_get_memory_data");
    auto memorySize = symbol<size_t(*)(unsigned)>(lib, "retro_get_memory_size");
    if (stateSize > 0 && stateSize <= 64 * 1024 * 1024) {
        std::vector<uint8_t> state(stateSize);
        auto ram = static_cast<uint8_t*>(memoryData(RETRO_MEMORY_SYSTEM_RAM));
        const size_t checkSize = ram ? std::min<size_t>(64, memorySize(RETRO_MEMORY_SYSTEM_RAM)) : 0;
        std::vector<uint8_t> savedRam(checkSize);
        if (checkSize) memcpy(savedRam.data(), ram, checkSize);
        stateSaved = symbol<bool(*)(void*, size_t)>(lib, "retro_serialize")(state.data(), state.size());
        run();
        if (checkSize) ram[0] ^= 0x5a;
        if (stateSaved) stateRestored = symbol<bool(*)(const void*, size_t)>(lib, "retro_unserialize")(state.data(), state.size());
        if (stateRestored && checkSize) stateRestored = memcmp(savedRam.data(), ram, checkSize) == 0;
        if (stateRestored) run();
    }
    const auto sramSize = symbol<size_t(*)(unsigned)>(lib, "retro_get_memory_size")(RETRO_MEMORY_SAVE_RAM);
    const auto sram = symbol<void*(*)(unsigned)>(lib, "retro_get_memory_data")(RETRO_MEMORY_SAVE_RAM);
    printf("{\"core\":\"%s\",\"profile\":\"%s\",\"frames\":%d,\"seconds\":%.4f,\"emulatedFPS\":%.2f,\"videoFPS\":%.2f,\"targetFPS\":%.3f,\"audioFrames\":%zu,\"audibleSamples\":%zu,\"sramBytes\":%zu,\"sramExposed\":%s,\"stateBytes\":%zu,\"stateSaved\":%s,\"stateRestored\":%s}\n",
        coreName.c_str(), profile.c_str(), frames, seconds, frames / seconds,
        measuredVideo / seconds, av.timing.fps, measuredAudio, measuredAudible,
        sramSize, sram && sramSize ? "true" : "false", stateSize,
        stateSaved ? "true" : "false", stateRestored ? "true" : "false");
    fflush(stdout);
    symbol<void(*)()>(lib, "retro_unload_game")();
    symbol<void(*)()>(lib, "retro_deinit")();
    dlclose(lib);
    return stateSaved && stateRestored ? 0 : 5;
}
