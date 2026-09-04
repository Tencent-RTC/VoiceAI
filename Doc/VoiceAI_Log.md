# VoiceAI 日志接口（TXVoiceAILog）使用文档

> 模块：`TXVoiceAILog @ TXLiteAVSDK`
>
> 本文档介绍 VoiceAI SDK 的统一日志配置接口 `TXVoiceAILog`，覆盖 **Windows / 桌面（C++）**、**Android（Java）**、**iOS / macOS（Objective-C）**、**HarmonyOS（ArkTS）** 四个平台。

---

## 一、功能简介

`TXVoiceAILog` 是 VoiceAI 模块的**统一日志配置入口**，为实时语音合成（TTS）、实时语音识别（ASR）、音频降噪播放（AudioDenoisePlayer）等能力提供一致的日志观测能力。它提供三个静态方法：

| 接口 | 作用 |
|---|---|
| `setLogLevel` | 设置 SDK 本地日志的输出级别（同时作用于落盘日志与回调日志） |
| `setLogPath` | 设置日志文件的落盘目录 |
| `setLogCallback` | 注册日志回调，SDK 将要打印的日志实时抛送给业务层 |

特点：

- **无状态门面**：`TXVoiceAILog` 本身不保存状态，仅把配置转发到 SDK 底层统一日志模块，四个平台语义完全一致。
- **与 TRTC 日志体系对齐**：日志级别沿用 TRTC 的 0–6 语义，接入方无需重新理解一套级别定义。
- **纯本地能力**：日志只在进程内/本地文件流转，**不会主动上报云端**。
- **全局生效**：一次配置对当前进程内所有 VoiceAI 子模块（TTS / ASR / 降噪）生效。

> 适用模块：`TXRealtimeTTS`、`TXRealtimeASR`、`TXAudioDenoisePlayer` 以及后续 VoiceAI 系列能力。

---

## 二、接口总览（四平台对照）

| 能力 | C++ | Android (Java) | iOS / macOS (ObjC) | HarmonyOS (ArkTS) |
|---|---|---|---|---|
| 头文件 / 包名 | `tx_voice_ai_log.h`（命名空间 `liteav`） | `com.tencent.voiceai.TXVoiceAILog` | `TXVoiceAILog.h` | `tx_voice_ai_log.ts` |
| 设置日志级别 | `TXVoiceAILog::setLogLevel(TXVoiceAILogLevel)` | `TXVoiceAILog.setLogLevel(TXVoiceAILogLevel)` | `+[TXVoiceAILog setLogLevel:]` | `TXVoiceAILog.setLogLevel(TXVoiceAILogLevel)` |
| 设置落盘路径 | `TXVoiceAILog::setLogPath(const char*)` | `TXVoiceAILog.setLogPath(String)` | `+[TXVoiceAILog setLogPath:]` | `TXVoiceAILog.setLogPath(path: string)` |
| 设置日志回调 | `TXVoiceAILog::setLogCallback(ITXVoiceAILogCallback*)` | `TXVoiceAILog.setLogCallback(TXVoiceAILogCallback)` | `+[TXVoiceAILog setLogCallback:]` | `TXVoiceAILog.setLogCallback(ITXVoiceAILogCallback \| null)` |
| 日志级别枚举 | `TXVoiceAILogLevel`（`kVerbose`…`kNone`） | `TXVoiceAILog.TXVoiceAILogLevel`（`VERBOSE`…`NONE`） | `TXVoiceAILogLevel`（`TXVoiceAILogLevelVerbose`…`TXVoiceAILogLevelNone`） | `TXVoiceAILogLevel`（`Verbose`…`None`） |
| 回调接口 | `class ITXVoiceAILogCallback` | `interface TXVoiceAILogCallback` | `protocol TXVoiceAILogCallback` | `interface ITXVoiceAILogCallback` |
| 回调方法 | `void onLog(const char* log, TXVoiceAILogLevel level)` | `void onLog(String log, TXVoiceAILogLevel level)` | `- (void)onLog:(NSString *)log level:(TXVoiceAILogLevel)level` | `onLog(log: string, level: TXVoiceAILogLevel): void` |
| 取消回调 | 传 `nullptr` | 传 `null` | 传 `nil` | 传 `null` |

---

## 三、日志级别

| 取值 | C++ | Java | ObjC | ArkTS | 含义 |
|:---:|---|---|---|---|---|
| 0 | `kVerbose` | `VERBOSE` | `TXVoiceAILogLevelVerbose` | `Verbose` | 输出所有级别的 Log |
| 1 | `kDebug` | `DEBUG` | `TXVoiceAILogLevelDebug` | `Debug` | 输出 DEBUG、INFO、WARNING、ERROR、FATAL |
| 2 | `kInfo` | `INFO` | `TXVoiceAILogLevelInfo` | `Info` | 输出 INFO、WARNING、ERROR、FATAL |
| 3 | `kWarn` | `WARN` | `TXVoiceAILogLevelWarn` | `Warn` | 输出 WARNING、ERROR、FATAL |
| 4 | `kError` | `ERROR` | `TXVoiceAILogLevelError` | `Error` | 输出 ERROR、FATAL |
| 5 | `kFatal` | `FATAL` | `TXVoiceAILogLevelFatal` | `Fatal` | 仅输出 FATAL |
| 6 | `kNone` | `NONE` | `TXVoiceAILogLevelNone` | `None` | 不输出任何 SDK Log（**默认**） |

> **说明**：底层日志模块不区分 `Verbose` 与 `Debug`，两者均映射为「输出全部级别日志」。回调中的 `level` 会回传为 `Verbose`。

---

## 四、各平台接入

> **通用原则：`setLogLevel` / `setLogPath` / `setLogCallback` 应在调用任何其它 VoiceAI 接口之前完成配置**，否则初始化阶段的日志可能丢失。

### 4.1 Windows / 桌面 (C++)

#### 引入

```cpp
#include "tx_voice_ai_log.h"   // 命名空间 liteav
```

- 接口通过宏 `LITEAVSDK_VOICEAI_LOG_API` 导出。Windows 下使用方**无需**定义 `LITEAV_EXPORTS`（该宏仅 SDK 自身构建时使用）。
- 链接 `TXLiteAVSDK.lib` 导入库，运行时保证 `TXLiteAVSDK.dll` 位于可执行文件目录或 `PATH` 中。

#### 示例

```cpp
#include <cstdio>
#include <string>
#include "tx_voice_ai_log.h"

class MyLogCallback : public liteav::ITXVoiceAILogCallback {
 public:
  void onLog(const char* log, liteav::TXVoiceAILogLevel level) override {
    // 注意：本回调运行在 SDK 内部日志线程，请勿执行耗时或阻塞操作。
    printf("[VoiceAI][%d] %s\n", static_cast<int>(level), log ? log : "");
  }
};

// 全局对象，保证生命周期覆盖 SDK 使用周期。
MyLogCallback g_log_callback;

void InitVoiceAILog() {
  // 1. 设置日志落盘目录（目录必须已存在且可写）
  liteav::TXVoiceAILog::setLogPath("C:\\Users\\YourName\\Documents\\voiceai_log");

  // 2. 设置日志级别
  liteav::TXVoiceAILog::setLogLevel(liteav::TXVoiceAILogLevel::kInfo);

  // 3. 设置日志回调（可选）
  liteav::TXVoiceAILog::setLogCallback(&g_log_callback);
}

void UninitVoiceAILog() {
  // 退出前取消回调，避免 SDK 继续回调已失效的对象
  liteav::TXVoiceAILog::setLogCallback(nullptr);
}
```

### 4.2 Android (Java)

#### 引入

```java
import com.tencent.voiceai.TXVoiceAILog;
```

> SDK 已内置 JNI 桥接（`com.tencent.voiceai.jni.TXVoiceAILogJni`），业务层只需使用 `TXVoiceAILog`，无需关心 native 层。

#### 示例

```java
import android.content.Context;
import android.os.Environment;
import com.tencent.voiceai.TXVoiceAILog;

public class VoiceAILogHelper {

    private static final TXVoiceAILog.TXVoiceAILogCallback sCallback =
            new TXVoiceAILog.TXVoiceAILogCallback() {
                @Override
                public void onLog(String log, TXVoiceAILog.TXVoiceAILogLevel level) {
                    // 注意：本回调运行在 SDK 内部日志线程（子线程），
                    // 更新 UI 需切到主线程。
                    android.util.Log.d("VoiceAI", "[" + level + "] " + log);
                }
            };

    public static void init(Context context) {
        // 1. 设置日志落盘目录（推荐应用私有目录，Android 10+ 避免使用公共目录）
        String path = context.getExternalFilesDir(null) + "/log/voiceai";
        // 若使用外部存储，请确保目录已存在且拥有写权限
        new java.io.File(path).mkdirs();
        TXVoiceAILog.setLogPath(path);

        // 2. 设置日志级别
        TXVoiceAILog.setLogLevel(TXVoiceAILog.TXVoiceAILogLevel.INFO);

        // 3. 设置日志回调（可选）
        TXVoiceAILog.setLogCallback(sCallback);
    }

    public static void uninit() {
        // 取消回调
        TXVoiceAILog.setLogCallback(null);
    }
}
```

> Android 侧回调对象由 JNI 层静态字段强引用，**不会被 GC 回收**；调用 `setLogCallback(null)` 即可取消。

### 4.3 iOS / macOS (Objective-C)

#### 引入

```objc
#import "TXVoiceAILog.h"
```

#### 示例

```objc
#import "TXVoiceAILog.h"

@interface VoiceAILogReceiver : NSObject <TXVoiceAILogCallback>
@end

@implementation VoiceAILogReceiver
- (void)onLog:(NSString *)log level:(TXVoiceAILogLevel)level {
    // 注意：本回调运行在 SDK 内部日志线程，如需刷新 UI 请 dispatch 到主线程。
    NSLog(@"[VoiceAI][%ld] %@", (long)level, log);
}
@end

// 使用强引用持有回调对象，避免提前释放
@property (nonatomic, strong) VoiceAILogReceiver *logReceiver;

- (void)setupVoiceAILog {
    // 1. 设置日志落盘目录（需为应用沙箱内可写目录，如 Documents/clogs）
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory,
                                                         NSUserDomainMask, YES);
    NSString *logPath = [paths.firstObject stringByAppendingPathComponent:@"voiceai_log"];
    [[NSFileManager defaultManager] createDirectoryAtPath:logPath
                              withIntermediateDirectories:YES
                                               attributes:nil
                                                    error:nil];
    [TXVoiceAILog setLogPath:logPath];

    // 2. 设置日志级别
    [TXVoiceAILog setLogLevel:TXVoiceAILogLevelInfo];

    // 3. 设置日志回调（可选）
    self.logReceiver = [[VoiceAILogReceiver alloc] init];
    [TXVoiceAILog setLogCallback:self.logReceiver];
}

- (void)teardownVoiceAILog {
    [TXVoiceAILog setLogCallback:nil];
    self.logReceiver = nil;
}
```

> iOS 上 `setLogPath:` 传入的绝对路径会被 SDK 记录为沙箱相对路径后再还原，请使用应用沙箱内的目录。

### 4.4 HarmonyOS (ArkTS)

#### 引入

```typescript
import { TXVoiceAILog, TXVoiceAILogLevel, ITXVoiceAILogCallback }
  from 'path/to/tx_voice_ai_log';
```

> ArkTS 层的 `TXVoiceAILog` 内部通过 NAPI（`libliteavsdk_voiceai.so`）转发到同一套 Native 实现，首次调用时会自动完成 Native 初始化。

#### 示例

```typescript
import { TXVoiceAILog, TXVoiceAILogLevel, ITXVoiceAILogCallback }
  from 'path/to/tx_voice_ai_log';
import { common } from '@kit.AbilityKit';

class VoiceAILogReceiver implements ITXVoiceAILogCallback {
  onLog(log: string, level: TXVoiceAILogLevel): void {
    // SDK 内部通过 ThreadSafeFunction 把回调投递到 JS 线程
    console.info(`[VoiceAI][${level}] ${log}`);
  }
}

const logReceiver: VoiceAILogReceiver = new VoiceAILogReceiver();

export function setupVoiceAILog(context: common.UIAbilityContext): void {
  // 1. 设置日志落盘目录（应用沙箱目录，需保证存在且可写）
  const logPath: string = `${context.filesDir}/voiceai_log`;
  TXVoiceAILog.setLogPath(logPath);

  // 2. 设置日志级别
  TXVoiceAILog.setLogLevel(TXVoiceAILogLevel.Info);

  // 3. 设置日志回调（可选）
  TXVoiceAILog.setLogCallback(logReceiver);
}

export function teardownVoiceAILog(): void {
  TXVoiceAILog.setLogCallback(null);
}
```

---

## 五、API 详解

### 5.1 setLogLevel —— 设置日志输出级别

| 平台 | 签名 |
|---|---|
| C++ | `static void setLogLevel(TXVoiceAILogLevel level)` |
| Java | `public static void setLogLevel(TXVoiceAILogLevel level)` |
| ObjC | `+ (void)setLogLevel:(TXVoiceAILogLevel)level` |
| ArkTS | `static setLogLevel(level: TXVoiceAILogLevel): void` |

- **默认值**：`None`（不输出任何 SDK 日志）。需要日志时必须显式调用本接口。
- **作用范围**：同时作用于**落盘日志**与**回调日志**，即级别过滤发生在回调分发之前，被过滤掉的日志不会通过 `onLog` 抛出。
- 级别可在运行期随时调整，立即生效。

### 5.2 setLogPath —— 设置日志落盘目录

| 平台 | 签名 |
|---|---|
| C++ | `static void setLogPath(const char* path)` |
| Java | `public static void setLogPath(String path)` |
| ObjC | `+ (void)setLogPath:(NSString*)path` |
| ArkTS | `static setLogPath(path: string): void` |

- **调用时机**：请务必在**所有其它 VoiceAI 接口之前**调用。
- **目录要求**：目录必须**已存在且可写**。若目录不存在或无写权限，SDK 不会崩溃，但可能降级为使用默认路径或停止落盘。
- 可在运行期修改，修改后新日志写入新目录。
- 不调用时使用 SDK 默认路径（见 [六、日志文件说明](#六日志文件说明)）。

### 5.3 setLogCallback —— 设置日志回调

| 平台 | 签名 |
|---|---|
| C++ | `static void setLogCallback(ITXVoiceAILogCallback* callback)` |
| Java | `public static void setLogCallback(TXVoiceAILogCallback callback)` |
| ObjC | `+ (void)setLogCallback:(nullable id<TXVoiceAILogCallback>)callback` |
| ArkTS | `static setLogCallback(callback: ITXVoiceAILogCallback \| null): void` |

- 传入回调对象即注册；传入 `nullptr` / `null` / `nil` 即**取消回调**，取消后不影响日志落盘。
- **仅保留最后一次注册的回调**：重复调用会覆盖前一个回调。
- 回调参数：

| 参数 | 说明 |
|---|---|
| `log` | 单条日志内容（已包含时间、级别、线程、模块等信息） |
| `level` | 该条日志的级别，取值见 [三、日志级别](#三日志级别) |

> **线程与生命周期注意事项（重要）**
>
> 1. `onLog` 在 **SDK 内部日志线程**触发，**不是**业务线程 / 主线程。回调中禁止执行耗时、阻塞、加锁等待等重操作，否则会拖慢甚至卡死日志线程。
> 2. 回调中**不要再调用会产生 SDK 日志的接口**，避免递归调用。
> 3. Android 上更新 UI 需切回主线程；ArkTS 侧 SDK 已通过 `ThreadSafeFunction` 自动投递到 JS 线程。
> 4. **C++**：`setLogCallback` 只保存裸指针，不接管所有权。必须保证回调对象在取消注册（`setLogCallback(nullptr)`）之前一直有效。
> 5. **Java / ObjC / ArkTS**：回调对象由 SDK 内部强引用持有，业务层仍建议自行持有引用以便主动取消。

---

## 六、日志文件说明

### 6.1 文件命名

SDK 在指定目录下生成如下命名的日志文件：

```
LiteAV_C_YYYYMMDD-<进程ID>.clog      // 压缩格式（默认）
LiteAV_R_YYYYMMDD-<进程ID>.clog      // 明文格式
LiteAV_C_YYYYMMDD-<进程ID>_01.clog   // 单文件超限后自动分片
```

- 前缀 `C` 表示压缩（Compressed），`R` 表示明文（Raw）。
- 默认**启用压缩**，`.clog` 文件为 zlib 压缩内容，需用解压脚本转换为 `.log` 后再阅读。

### 6.2 解压日志

SDK 随包提供 Python 解压脚本 `tools/decompress_clog.py`：

```bash
# 解压单个文件
python decompress_clog.py LiteAV_C_20260902-12345.clog

# 解压整个目录（自动向下递归 5 层）
python decompress_clog.py ./voiceai_log
```

解压后生成同名 `.log` 明文文件，可直接用文本编辑器打开。

### 6.3 轮转与清理策略

| 策略 | 取值 |
|---|---|
| 单文件上限 | 10 MB，超出后新建分片文件 |
| 目录总容量上限 | 200 MB，超出后按修改时间从旧到新删除，直至降到约 1/3 容量 |
| 日志保留天数 | Linux 15 天，其它平台 10 天，过期自动删除 |

### 6.4 各平台默认落盘路径

未调用 `setLogPath` 时，SDK 使用以下默认目录：

| 平台 | 默认路径 |
|---|---|
| Windows | `%appdata%/voiceai/liteav/log` |
| Android | `/sdcard/Android/data/<packageName>/files/voiceai/log/liteav/` |
| iOS / macOS | `Documents/voiceai/log` |
| HarmonyOS | `/data/app/el2/100/base/<bundleName>/files/voiceai/liteav/log/` |

> `<packageName>` / `<bundleName>` 为应用的包名 / Bundle 名。

> 曾调用过 `setLogPath` 的路径会被持久化记录，下次启动未设置时沿用上次设置（优先级：本次设置 > 上次设置 > 默认路径）。

---

## 七、注意事项与最佳实践

1. **尽早配置**：在 Application / Ability / `main()` 启动阶段、使用任何 VoiceAI 能力之前完成日志配置，确保能抓到初始化阶段的日志。
2. **目录先建后用**：调用 `setLogPath` 前请确保目录已 `mkdirs` 创建成功，并确认具备写权限。
3. **生产环境建议**：
   - 正式发布版本建议设置为 `Info` / `Warn` 及以上，或 `None` 关闭日志，避免性能损耗与磁盘占用；
   - 排障时临时开启 `Verbose` / `Debug`，并配合 `setLogCallback` 实时抓取。
4. **回调轻量**：`onLog` 运行在 SDK 日志线程，只做「转存 / 打印 / 投递到业务线程」，不做耗时处理。
5. **及时取消**：模块销毁前调用 `setLogCallback(nullptr/null/nil)`，避免悬垂指针或对象泄漏。
6. **隐私合规**：日志中可能包含设备信息、文件路径等敏感内容，日志**不会主动上传云端**；如需回传服务器或随工单提交，请自行脱敏。
7. **多模块共用**：TTS / ASR / 降噪共用同一份日志配置，无需按模块分别设置。

---

## 八、常见问题（FAQ）

**Q1：调用了 `setLogPath`，但目录下没有日志文件？**

A：依次检查：① 日志级别是否为 `None`（默认是 `None`，需调用 `setLogLevel` 打开）；② 目录是否存在且有写权限；③ 是否真的产生了日志（未调用任何 VoiceAI 能力时不会有日志）。

**Q2：`.clog` 文件打不开 / 是乱码？**

A：`.clog` 为压缩格式，请使用 `tools/decompress_clog.py` 解压后再查看（见 [6.2](#62-解压日志)）。

**Q3：设置了回调，为什么收不到日志？**

A：回调同样受 `setLogLevel` 级别过滤影响，请确认级别已打开；另外请确保注册发生在产生日志之前。

**Q4：可以注册多个回调吗？**

A：不可以。`TXVoiceAILog` 全局仅保留最后一次注册的回调，如需分发给多个接收方，请在自己的回调实现中做二次分发。

**Q5：回调是在主线程吗？能直接更新 UI 吗？**

A：不是。`onLog` 在 SDK 内部日志线程触发，除 ArkTS（已自动切回 JS 线程）外，Android / iOS / C++ 都需要自行切回主线程再更新 UI。

**Q6：`setLogLevel` 与 `setLogPath` 有调用顺序要求吗？**

A：两者之间没有顺序要求，但都应**早于**其它 VoiceAI 接口调用。推荐顺序：`setLogPath` → `setLogLevel` → `setLogCallback`。

**Q7：日志会上传服务器吗？**

A：不会。日志仅在本地落盘或通过 `onLog` 抛给业务层，**默认不上报云端**，业务层需自行决定如何处理回调到的日志内容。

---

## 九、接口源码位置

| 平台 | 公开头文件 |
|---|---|
| C++ | `sdk/api/voiceai/cpp/tx_voice_ai_log.h` |
| Android | `sdk/api/voiceai/java/TXVoiceAILog.java` |
| iOS / macOS | `sdk/api/voiceai/oc/TXVoiceAILog.h` |
| HarmonyOS | `sdk/api/voiceai/ts/tx_voice_ai_log.ts` |

四端最终都转发到同一份 Native 实现：`sdk/voiceai/cpp/tx_voice_ai_log.cc` → `liteav_base::LogSetting`。
