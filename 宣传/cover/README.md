# Miku Camera 视频封面

参考 MDmeow 的 `v5-web-promo/cover-blue`：得意黑倾斜大标题、奶油底、初音绿、实机截图拼贴和小表情贴纸。截图直接引用本项目素材，保留实拍水印与 AI 示例画面。

- `miku-camera-cover-4K.jpg` / `.png`：3840×2160，16:9 主封面。
- `miku-camera-cover-1080p.jpg`：1920×1080，可直接上传。
- `miku-camera-cover-4x3.jpg` / `.png`：2880×2160，同一画面的中心裁切。
- `miku-camera-cover-4x3-preview.jpg`：1440×1080 裁切预览。

16:9 母版中央 75% 宽度为 4:3 安全区。标题、功能副标题、短句及项目地址均经过文字边界校验；截图主体位于中心区域。画面边缘仅允许装饰和卡片外缘出血。两种画幅不重新排版。

源文件 `cover-design.html` / `cover-design.css` 可继续编辑。在项目根目录执行 `node 宣传/cover/render-cover.mjs` 重建，自动输出尺寸与文字安全区检查记录 `miku-camera-cover-metadata.json`。
