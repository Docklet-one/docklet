# Docklet 蜜蜂图标资源

所有图形都是矢量绘制，背景透明；改圆角、颜色、蜜蜂大小请改生成参数后重跑脚本，不要重新识图描边。

## 生成代码（本地，不推仓库）

位于 `.tools/icon/`：

- `params.json`：圆角、渐变、蜜蜂缩放/位置、前景缩放、颜色
- `gen-svg.mjs`：读取参数生成 SVG

```bash
node .tools/icon/gen-svg.mjs
```

改完 SVG 后按原流程渲染 PNG（headless Chrome 截图 / Pillow 缩放）即可。

## 文件

| 文件 | 用途 |
| --- | --- |
| `icon-rounded.svg` / `.png` | 512 圆角图标（圆角由参数控制），官网、商店、预览 |
| `icon-square.svg` / `.png` | 512 满幅方形、不切角；旧版 Android 桌面图标使用 |
| `adaptive-foreground.svg` / `.png` | 512 透明前景，已按自适应安全区缩放 |
| `mark-bee.svg` / `.png` | 240×320 透明蜜蜂标，启动页/空状态/关于页可用 |

## Android

- 自适应图标（8.0+）：`@color/ic_launcher_background` `#FFD15C` + `ic_launcher_foreground.png`
- **不自带圆角**，形状由系统/主题决定，方形主题也能正常显示
- 旧版（7.x 及以下）：`ic_launcher.png` / `ic_launcher_round.png` 使用 `icon-square`，同样不切圆角
- 尺寸：旧版 48/72/96/144/192；自适应前景 108/162/216/324/432

## 颜色

- 背景渐变：`#FFE28A → #FFCB4F → #FFB23E`
- 自适应背景：`#FFD15C`
- 蜜蜂身体：`#FFC33C`，条纹 `#3B2A1A`
- 翅膀：`#FFF3CE`，描边 `#B9852F`
- D：`#FFFFFF`
