# MobileCAD 中文使用手册 / MobileCAD user manual (Simplified Chinese)

一本从零开始的 MobileCAD 教学手册：功能介绍、操作步骤、完整练习，覆盖草图、零件设计、零件、Draft、
工程图、变量与表达式、装配、有限元、CAM、BIM、网格/点云与文件交换。

## 文件

| 文件 | 说明 |
|---|---|
| `main.tex` | 主文件（cTeXbook，XeLaTeX） |
| `chapters/` | 各章（前言、17 章正文） |
| `appendix/` | 附录 A 命令速查、B 中英术语、C 常见问题 |
| `figures/` | 截图（应用实机截图 + 项目官方素材） |
| `MobileCAD-manual-zh-Hans.pdf` | 编译好的 PDF（89 页） |
| `en/` | 英文版（同一内容的英文重写 + i18n 机制说明），见 `en/README.md` |
| `en/MobileCAD-manual-en.pdf` | 英文版 PDF（89 页） |

## 编译

中文版：`cd manual && xelatex main.tex`（两遍）。英文版：`cd manual/en && xelatex main.tex`（两遍），
分别依赖 ctex / xeCJK 与中文字体。

### 环境

需要 XeLaTeX 与 `ctex`、`fontawesome5`、`newunicodechar`、`tcolorbox`、`seqsplit`、`longtable` 等宏包
（TeX Live / MacTeX 完整安装均自带）。macOS 使用系统字体 Songti SC / Heiti SC；其它系统请修改
`main.tex` 里 `\setCJKmainfont` 等四行。

```sh
cd manual
xelatex main.tex
xelatex main.tex     # 第二遍生成目录与交叉引用
```

## 许可

与 MobileCAD 相同：GNU General Public License v3.0 or later。

## 反馈

发现与当前版本不符之处，请在仓库提 issue 并标注 `manual`，或直接提 PR 修改对应 `.tex` 文件。
