# 轮班查询

一个简洁的 Android App，用于查询轮班日期是上班还是休息。

## 功能

- **值班类型**：上一休二 / 上一休三 / 上一休四
- **基准日期**：设定起始上班日
- **选择日期**：点击查询任意日期是上班还是休息
- **自动计算**：以基准日期为第 1 个上班日，按周期循环推算

## 使用方法（无需 Android Studio）

### 1. 推送到 GitHub

```bash
cd ShiftChecker
git init
git add .
git commit -m "initial commit"
git branch -M main
git remote add origin https://github.com/你的用户名/你的仓库名.git
git push -u origin main
```

### 2. 自动打包

推送后，进入 GitHub 仓库 → **Actions** 标签页，可以看到构建任务在运行。

构建完成后，进入对应任务的 **Artifacts** 区域，下载 `shiftchecker-release-apk.zip`，解压即可获得 APK 文件。

### 3. 安装到手机

将 APK 传到手机，直接安装即可。

## 手动打包（可选）

如果你本地有 JDK 17 + Gradle：

```bash
cd ShiftChecker
gradle wrapper --gradle-version 8.2
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/release/app-release-unsigned.apk`

## 技术栈

- Kotlin + Jetpack Compose
- Material 3 组件
- `java.time.LocalDate` 日期计算
- minSdk 26 (Android 8.0)
