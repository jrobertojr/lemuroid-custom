#include <libretro.h>
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
static bool env(unsigned cmd, void* data) {
 static const char* dir="/data/user/0/com.swordfish.lemuroid.debug/cache/core-probe-extra";
 if(cmd==RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY||cmd==RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY){*(const char**)data=dir;return true;}
 if(cmd==RETRO_ENVIRONMENT_SET_PIXEL_FORMAT||cmd==RETRO_ENVIRONMENT_SET_VARIABLES||cmd==RETRO_ENVIRONMENT_SET_CONTROLLER_INFO||cmd==RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS)return true;
 return false;
}
static void video(const void*,unsigned,unsigned,size_t){}
static void audio(int16_t,int16_t){}
static size_t batch(const int16_t*,size_t n){return n;}
static void poll(){}
static int16_t input(unsigned,unsigned,unsigned,unsigned){return 0;}
#define CALL(n) reinterpret_cast<decltype(&n)>(dlsym(h,#n))
int main(int argc,char**argv){
 void*h=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);if(!h){puts(dlerror());return 1;}
 CALL(retro_set_environment)(env);
 CALL(retro_set_video_refresh)(video);CALL(retro_set_audio_sample)(audio);CALL(retro_set_audio_sample_batch)(batch);
 CALL(retro_set_input_poll)(poll);CALL(retro_set_input_state)(input);
 CALL(retro_init)();
 retro_system_info info{};CALL(retro_get_system_info)(&info);printf("Callback-before-init PASS: %s %s\n",info.library_name,info.library_version);
 CALL(retro_deinit)();
 return 0;
}
