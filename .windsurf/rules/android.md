---
trigger: always_on
description: 
globs: 
---

## Description
本规则适用于所有 Android 开发场景，包括 Kotlin/Java 编码、Jetpack 组件使用、协程异步处理、Gradle 依赖管理以及 UI 架构（Compose/View）。当涉及到 Android 平台特有逻辑时，Cascade 必须严格遵守此规范。

## 1. 核心技术栈与架构约束
* **开发语言:** 优先使用 **Kotlin**。编写 Kotlin 代码时，应充分利用现代语言特性（如 Scope Functions, Extension Functions, Seales Classes 等），杜绝 Java 式的繁琐写法。
* **架构模式:** 遵循 **MVVM / MVI** 架构与单向数据流（UDF）。ViewModel 负责状态管理与业务逻辑，View 层只负责渲染状态和分发 Intent/Action。
* **异步处理:** 坚决禁止引入 `GlobalScope`。必须使用 `viewModelScope` 或 `lifecycleScope`。
    * 耗时操作（IO、数据库、网络）必须显式切到 `Dispatchers.IO`。
    * CPU 密集型操作使用 `Dispatchers.Default`。
* **依赖管理:** 如果项目根目录下存在 `gradle/libs.versions.toml`，在添加或修改任何依赖时，**必须**使用 Version Catalog 进行统一管理，禁止在 `build.gradle(.kts)` 中硬编码版本号。

## 2. 内存泄漏与稳定性防护 (重点)
* **Context 传递:** 严禁在 ViewModel、单例、静态变量或生命周期长于 Activity/Fragment 的对象中持有 Activity 的 Context。如需使用，必须优先考虑 `applicationContext`。
* **Lifecycle 安全:** * 在 View 层收集（Collect）Kotlin Flow 时，必须使用 `repeatOnLifecycle` 或 `flowWithLifecycle` 以确保在应用切到后台时自动挂起，避免资源浪费。
    * 使用 ViewBinding 时，Fragment 中的 binding 变量必须在 `onDestroyView()` 中显式置为 `null`，防止内存泄漏。

## 3. UI 渲染规范 (Jetpack Compose & View)
### 当使用 Jetpack Compose 时：
* **状态提升 (State Hoisting):** 保持 Composable 函数的纯净与可复用，尽可能将状态提升到上层，使其变为无状态（Stateless）组件。
* **重组优化:** * 避免在 Composable 作用域内直接执行耗时计算，必须使用 `remember` 或 `derivedStateOf`。
    * 向 Composable 传递不稳定集合（如 List, Map）或 Lambda 时，注意使用 `@Stable` / `@Immutable` 或 `rememberUpdatedState` 防止不必要的重组。
    * Preview 必须提供完整的 Mock 数据。

### 当使用传统 View & XML 时：
* 禁止硬编码颜色、尺寸和字符串。所有资源必须写入 `strings.xml`、`colors.xml`、`dimens.xml`。
* 禁止使用 `findViewById`，一律采用 ViewBinding。

## 4. 专项治理与代码整洁度
* **混淆安全:** 编写涉及 JNI、反射（尤其是通过系统类链式反射）、序列化/反序列化（Gson/Serialization）的数据模型（Serializable/Parcelable）时，必须同时检查或提醒修改 `proguard-rules.pro` 保持文件，防止 release 包混淆崩溃。
* **日志控制:** 生产环境严禁直接使用 `Log.d` / `Log.e`。必须使用项目中封装的统一日志工具类（如 Timber），且敏感信息（如 Token、身份证号、设备敏感 ID）严禁打印。

## 5. Cascade 交互指令
* 在重构或生成逻辑代码后，优先尝试生成对应的 **Unit Test（单元测试）**，优先使用 Mockk 进行依赖 Mock。
* 如果涉及修改系统级 API 绕过（如 Hidden API）或底层 Binder 通信，在生成方案前，必须先在注释或回复中阐明技术链路与潜在的兼容性风险。