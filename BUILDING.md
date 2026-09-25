# Building HuhobotMonitor

要求 JDK 21 用于构建；产物通过 `--release 8` 保持 Java 8 字节码。

先在同级 `PenguinClient-Main` 中构建 `common:Bot`，然后在本目录运行：

```powershell
.\gradlew.bat clean test build renderPreview
```

如果 HuHoBot 主分支 JAR 不在默认位置，复制 `gradle.properties.example` 为 `gradle.properties` 并填写绝对路径，或设置 `HUHOBOT_QQ_SDK_JAR`。
