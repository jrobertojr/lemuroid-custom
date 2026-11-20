// Android on-device ABI/dependency probe. Does not load a ROM or write user saves.
#include <libretro.h>
#include <dlfcn.h>
#include <cstdio>
#include <string>

static std::string quoted(const char* value) {
    std::string out = "\"";
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(value ? value : ""); *p; ++p) {
        if (*p == '"' || *p == '\\') { out += '\\'; out += *p; }
        else if (*p >= 32) out += *p;
    }
    return out + "\"";
}
int main(int argc, char** argv) {
    if (argc != 2) { std::fprintf(stderr, "Usage: core-probe core.so\n"); return 2; }
    void* handle = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!handle) { std::fprintf(stderr, "%s\n", dlerror()); return 3; }
    auto version = reinterpret_cast<unsigned(*)()>(dlsym(handle, "retro_api_version"));
    auto info = reinterpret_cast<void(*)(retro_system_info*)>(dlsym(handle, "retro_get_system_info"));
    const char* required[] = {"retro_init", "retro_deinit", "retro_set_environment", "retro_load_game",
        "retro_unload_game", "retro_run", "retro_set_video_refresh", "retro_set_audio_sample_batch",
        "retro_set_input_poll", "retro_set_input_state", "retro_serialize", "retro_unserialize"};
    if (!version || !info || version() != RETRO_API_VERSION) return 4;
    for (auto name : required) if (!dlsym(handle, name)) { std::fprintf(stderr, "Missing %s\n", name); return 4; }
    retro_system_info data{}; info(&data);
    std::printf("{\"path\":%s,\"name\":%s,\"version\":%s,\"extensions\":%s,\"needFullpath\":%s,\"blockExtract\":%s}\n",
        quoted(argv[1]).c_str(), quoted(data.library_name).c_str(), quoted(data.library_version).c_str(),
        quoted(data.valid_extensions).c_str(), data.need_fullpath ? "true" : "false", data.block_extract ? "true" : "false");
    dlclose(handle);
    return 0;
}
