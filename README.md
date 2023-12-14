# ResourceOpt

#### 添加依赖
```agsl
// 项目根目录的build.gradle
 repositories {
    maven { url "https://raw.githubusercontent.com/eric-lian/Maven/master" }
}
dependencies {
    classpath "com.resourcesopt.plugin:ResourcesOpt:1.0-test04"
}

// app目录下的build.gradle
plugins {
    id "resourcesopt"
}

// 配置
resourceOpt {
    repeatResOptEnable = true
    resNameOptEnable = true
    resNameOptWhiteRegexList = ["^yd_.*"]
    resNameOptPlaceholder = "yd"
}
```