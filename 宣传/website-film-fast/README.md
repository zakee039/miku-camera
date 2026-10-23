# Miku Camera · 50 秒配音版

成片 `miku-camera-50s-1080p.mp4`：1920×1080，30 fps，50 秒，H.264 + AAC。

基于确认后的网页布局，保持居中、左右交替、截图无外加白边。完整保留 10 段故事线和 13 张不同截图，页面说明已精简为可快速扫读的功能介绍。向左切页时长 0.38 秒，最后停在项目主页。右上角与尾页显示 `camera.zakee.fun`，无其他演示按钮。

中文合成女声逐页解说，附画面字幕和 `captions.srt`。使用用户指定的《月が綺麗ねと言われたい！》作为 BGM；配音时压低音乐，最后 1.3 秒淡出。时长由内容决定，不随歌曲时长拉伸。

每页时长：4.5、4.5、5.5、5、4.5、5.5、5、4.5、6、5 秒。

重建（项目根目录）：

```powershell
node 宣传/website-film-fast/build-scenes.mjs
python 宣传/website-film-fast/prepare-audio.py
node 宣传/render.mjs --html website-film-fast/film.html --out website-film-fast/miku-camera-50s-1080p.mp4 --audio website-film-fast/audio/mix.wav --duration 50 --fps 30 --preset medium --crf 18
python 宣传/website-film-fast/validate.py
```

`build-scenes.mjs` 只建立视频副本，不改动 `docs/`。`prepare-audio.py` 中保存完整配音脚本，使用内容摘要缓存语音。分镜、字幕、音轨对齐数据见 `timing.json`，歌曲来源与混音参数见 `audio-source.json`。验证报告及成片抽帧见 `qa/`。
