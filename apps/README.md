# apps/

各模块的源码放在这里，一个模块一个子目录：

```text
apps/
├─ notes/
│  ├─ module.json
│  ├─ index.html
│  └─ app.js
└─ racer/
   └─ ...
```

规则：

```text
由「模块」对话维护，壳对话不修改本目录内容
壳构建时会扫描 apps/*/module.json，把模块打进 APK
仓库里这里是空的：克隆后编译得到空壳，模块从在线目录安装
```

目录必须存在（哪怕为空），`build.gradle` 依赖它。
