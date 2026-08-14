# ASMR Lab Android

一个面向 ASMR 音频/字幕处理场景的 Android 小工具，支持：

- 批量把 VTT 字幕转换为 LRC。
- 批量把 WAV 音频转换为 MP3。
- 可添加单个文件或多个文件夹。
- 转换完成后可点击查看链接，调起系统文件查看器定位输出文件。

## 主要功能

### 字幕转换

- 输入：VTT 文件或包含 VTT 的文件夹。
- 输出：与源文件同目录的 `.lrc` 文件。
- 支持两种时间格式：
  - `[MM:SS.mmm]`
  - `[HH:MM:SS.mmm]`
- 默认 `[MM:SS.mmm]`。
- 不自动翻译，只做格式转换；VTT 内联标签会清理，但保留原有文本行结构。

### 音频转换

- 输入：WAV 文件或包含 WAV 的文件夹。
- 输出：与源文件同目录的 `.mp3` 文件。
- 使用 FFmpegKit 和 libmp3lame 编码。
- 可选码率：320 / 256 / 192 / 128 kbps，默认 320 kbps。

## 输出目录

- 文件夹模式：输出到源文件所在目录。
- 单个文件模式：
  - 授权“所有文件访问”后，输出到源文件所在目录。
  - 未授权或系统不允许时，输出到：
    `Android/data/com.asmr.tools/files/ASMR输出`

## 构建

环境要求：

- Android SDK 35
- JDK 17 或更高
- Gradle 8.9

构建 Debug APK：

```bash
gradle assembleDebug
```

构建 Release APK：

```bash
gradle assembleRelease
```

如果本机有签名密钥，请复制 `keystore.properties.example` 为 `keystore.properties` 并填写实际值。未配置签名文件时，Release 包将不签名，仅用于调试构建。

## 技术栈

- Android 原生 Java
- Android Storage Access Framework
- FFmpegKit
- AndroidX DocumentFile / Core
