plugins { id("com.android.library") }

android {
    namespace = "com.swordfish.lemuroid.core.dsperate"
    ndkVersion = "27.2.12479018"
    defaultConfig {
        ndk {
            abiFilters += (findProperty("dsperateAbis") as? String)?.split(",")
                ?: listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
