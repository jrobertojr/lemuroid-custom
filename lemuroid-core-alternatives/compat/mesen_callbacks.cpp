// SPDX-License-Identifier: GPL-3.0-or-later
// Mesen's older ports dereference objects created in retro_init from callback setters.
// Libretro permits those setters before init. Delay them, preserving the frontend's order.
#include <libretro.h>
#include <dlfcn.h>
#include <cstdlib>
#include <string>

static void* library() {
    static void* handle = [] {
        Dl_info info{};
        if (!dladdr(reinterpret_cast<void*>(&library), &info)) std::abort();
        std::string path(info.dli_fname);
        path.resize(path.find_last_of('/') + 1);
        path += ORIGINAL_LIBRARY;
        void* result = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!result) std::abort();
        return result;
    }();
    return handle;
}
template<typename T> static T symbol(const char* name) {
    auto value = reinterpret_cast<T>(dlsym(library(), name));
    if (!value) std::abort();
    return value;
}
#define REAL(name) symbol<decltype(&name)>(#name)
static bool initialized = false;
static retro_video_refresh_t video;
static retro_audio_sample_t audio;
static retro_audio_sample_batch_t batch;
static retro_input_poll_t poll;
static retro_input_state_t input;

extern "C" {
void retro_set_video_refresh(retro_video_refresh_t callback) { video = callback; if (initialized) REAL(retro_set_video_refresh)(callback); }
void retro_set_audio_sample(retro_audio_sample_t callback) { audio = callback; if (initialized) REAL(retro_set_audio_sample)(callback); }
void retro_set_audio_sample_batch(retro_audio_sample_batch_t callback) { batch = callback; if (initialized) REAL(retro_set_audio_sample_batch)(callback); }
void retro_set_input_poll(retro_input_poll_t callback) { poll = callback; if (initialized) REAL(retro_set_input_poll)(callback); }
void retro_set_input_state(retro_input_state_t callback) { input = callback; if (initialized) REAL(retro_set_input_state)(callback); }
void retro_init() {
    REAL(retro_init)();
    initialized = true;
    REAL(retro_set_video_refresh)(video);
    REAL(retro_set_audio_sample)(audio);
    REAL(retro_set_audio_sample_batch)(batch);
    REAL(retro_set_input_poll)(poll);
    REAL(retro_set_input_state)(input);
}
void retro_deinit() { REAL(retro_deinit)(); initialized = false; }
void retro_set_environment(retro_environment_t callback) { REAL(retro_set_environment)(callback); }
unsigned retro_api_version() { return REAL(retro_api_version)(); }
void retro_get_system_info(retro_system_info* info) { REAL(retro_get_system_info)(info); }
void retro_get_system_av_info(retro_system_av_info* info) { REAL(retro_get_system_av_info)(info); }
void retro_set_controller_port_device(unsigned port, unsigned device) { REAL(retro_set_controller_port_device)(port, device); }
void retro_reset() { REAL(retro_reset)(); }
void retro_run() { REAL(retro_run)(); }
size_t retro_serialize_size() { return REAL(retro_serialize_size)(); }
bool retro_serialize(void* data, size_t size) { return REAL(retro_serialize)(data, size); }
bool retro_unserialize(const void* data, size_t size) { return REAL(retro_unserialize)(data, size); }
void retro_cheat_reset() { REAL(retro_cheat_reset)(); }
void retro_cheat_set(unsigned index, bool enabled, const char* code) { REAL(retro_cheat_set)(index, enabled, code); }
bool retro_load_game(const retro_game_info* info) { return REAL(retro_load_game)(info); }
bool retro_load_game_special(unsigned type, const retro_game_info* info, size_t count) { return REAL(retro_load_game_special)(type, info, count); }
void retro_unload_game() { REAL(retro_unload_game)(); }
unsigned retro_get_region() { return REAL(retro_get_region)(); }
void* retro_get_memory_data(unsigned id) { return REAL(retro_get_memory_data)(id); }
size_t retro_get_memory_size(unsigned id) { return REAL(retro_get_memory_size)(id); }
}
