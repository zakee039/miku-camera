# Miku Camera 网页故事线视频

成片：`miku-camera-website-1080p.mp4`，1920×1080，30 fps，H.264 / AAC，1 分 52 秒。

基于最终确认的 `docs/` 网页制作独立视频，网站文件不做修改。奶油色底、墨绿文字、居中 1280px 内容区、左右交替的截图与功能说明保持一致。只在右上角及尾页显示 `camera.zakee.fun`，移除下载、查看项目、返回首页等交互按钮及底部控制栏。手机截图内的应用控件属于实际界面，保留原样。

13 张截图各出现一次：桌面图标 → 相机 → 水印列表与编辑 → API 配置与协议 → 设置 → AI 原图/提示词 → 桌面悬浮窗 → 公式服结果 → 参考造型与保存 → 项目地址。页面向左切换，顶部品牌和地址固定，最后一页停留，不循环。

先按内容安排各页阅读时长，再剪音乐：10、11、13、13、11、12、11、10、13、8 秒，包含切页时间。使用用户指定的《月が綺麗ねと言われたい！》OGG，从开头截取 112 秒，最后 2.5 秒淡出；无额外配音。源路径、文件摘要及音频处理记录见 `audio-source.json`。

## 修改和重建

在项目根目录执行：

```powershell
node 宣传/website-film/build-scenes.mjs
python 宣传/website-film/prepare-audio.py
node 宣传/render.mjs --html website-film/film.html --out website-film/miku-camera-website-1080p.mp4 --audio website-film/audio/bgm.wav --duration 112 --fps 30 --preset medium --crf 18
python 宣传/website-film/validate.py
```

`build-scenes.mjs` 从网站建立视频专用副本，生成分镜 PNG 和 `timing.json`。更改内容时编辑网站来源或该脚本；修改时长在脚本 `lengths` 中调整，并将导出命令的 `--duration` 同步为新总时长。`film.html` 用于独立预览，预览音频控制器在画布外，不进入 MP4。

验证内容：完整解码、画幅与帧率、10 段故事和 13 张截图覆盖、所有章节编码画面与源分镜对照、音频与指定歌曲剪辑的一致性、峰值不削波。报告及编码后检查图保存在 `qa/`。
