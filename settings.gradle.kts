// 国内镜像前置、官方仓库兜底:先命中镜像,镜像缺件时自动回落到 google()/mavenCentral(),
// 因此非国内网络同样可用。需要强制官方源时注释掉 maven(...) 行即可。
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
    }
}
rootProject.name = "LeiFetch"
include(":app")
