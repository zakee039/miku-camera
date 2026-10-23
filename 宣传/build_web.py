"""Keep the website's story in source screenshot order, with each shot used once."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
STORY = [
    dict(id='home', label='从桌面打开', tag='MIKU CAMERA / ANDROID',
         title='从桌面，<br><em>打开 Miku Camera</em>',
         description='一款原生 Android 相机：拍摄 Miku 水印合影，也支持用 AI 将 Miku 融入照片。',
         features=[('普通模式', '内置 Miku 水印，打开即可拍摄。'), ('AI 模式', '从拍摄或相册选图开始，生成新的互动场景。')],
         shots=[('desktop', '桌面上的 miku camera 图标')], sticker='hello'),
    dict(id='camera', label='相机拍摄', tag='01 / CAMERA',
         title='进入取景框，<br><em>直接预览水印</em>',
         description='Miku、时间与地点显示在取景画面中。取景框与成片保持一致，支持前后镜头及横竖屏拍摄。',
         features=[('对焦与曝光', '点击画面对焦，调整曝光和闪光灯。'), ('从拍摄开始', '普通模式直接合成水印，AI 模式使用干净原图创作。')],
         shots=[('camera', '普通模式取景界面')], sticker='happy'),
    dict(id='watermark', label='选择与编辑水印', tag='02 / WATERMARK',
         title='选择水印，<br><em>再调整它的样式</em>',
         description='先从列表选择或新建预设，再上传 PNG，调整位置、旋转与描边，按需显示时间和地点。',
         features=[('水印列表', '新建、命名、编辑或删除，记住上次选择。'), ('独立排版', '时间与地点不随 PNG 旋转，可选择门牌显示。')],
         shots=[('presets', '选择 / 新建水印'), ('watermark', '编辑水印与显示选项')], sticker='shy'),
    dict(id='api', label='API 配置', tag='03 / API CONFIGURATION',
         title='配置 AI 服务，<br><em>按需切换模型</em>',
         description='保存多套 API 配置，选择当前使用的服务；新增时设置协议、API Key、Base URL 与图像模型。',
         features=[('多套配置', '在已保存的配置之间切换，也可以继续编辑。'), ('多协议支持', 'OpenAI、Gemini、火山方舟、通义千问及 OpenAI 兼容接口。')],
         shots=[('api-configs', '选择当前 API 配置'), ('providers', '新增配置与协议选择')], sticker='happy'),
    dict(id='settings', label='相机设置', tag='04 / CAMERA SETTINGS',
         title='设置默认模式，<br><em>保存创作偏好</em>',
         description='选择默认进入水印相机或 AI 模式，编辑默认提示词，并设置下次创作使用的参考图。',
         features=[('形象参考预设', '更换或清除默认参考图，提示词页可直接调用。'), ('服务与应用', '进入 API 配置，查看版本、应用说明与作者主页。')],
         shots=[('settings', '默认启动、提示词与参考图设置')], sticker='flowers'),
    dict(id='ai', label='AI 选图与提示词', tag='05 / AI CREATION',
         title='选好原图，<br><em>确认这一张的创作</em>',
         description='拍摄或导入原图与参考图，编辑提示词；选择二次元或真人、服装、分辨率及时间地点水印。',
         features=[('形象与服装', '二次元 / 真人，公式服 / 非公式服。'), ('生成参数', '1K / 2K / 4K；确认提示词后开始生成。')],
         shots=[('prompt', '原图、参考图与生成参数')], sticker='shy',
         note='AI 模式需使用你配置的 API 服务。'),
    dict(id='background', label='桌面悬浮窗', tag='06 / BACKGROUND TASKS',
         title='回到桌面，<br><em>也能查看生成进度</em>',
         description='AI 任务在后台继续处理，通过可拖动的悬浮窗跟进进度，不必一直停留在生成页面。',
         features=[('同时查看多项任务', '窗口列出形象、服装、分辨率与当前状态。'), ('状态随任务更新', '截图展示四项任务，包括重新提交和正在生成。')],
         shots=[('background', '桌面上的 AI 任务悬浮窗')], sticker='happy'),
    dict(id='styles', label='公式服生成结果', tag='07 / CLASSIC OUTFIT',
         title='同一处风景，<br><em>两种形象的结果</em>',
         description='先看公式服的生成结果：二次元与真人写实分别融入同一张山间溪流照片。',
         features=[('二次元形象', '保留动漫风格，与实拍风景组合。'), ('真人写实形象', '用写实风格呈现 Miku，并生成时间地点水印。')],
         shots=[('anime', '二次元 · 公式服'), ('real', '真人写实 · 公式服')], sticker='love',
         note='应用内实际生成示例，效果随模型与输入变化。'),
    dict(id='save', label='参考造型与保存', tag='08 / REFERENCE OUTFIT & SAVE',
         title='再看参考造型，<br><em>对比并保存结果</em>',
         description='同一组原图与参考图，继续查看非公式服的二次元、真人结果。按住图片对比原图，再决定保存内容。',
         features=[('继续修改', '返回修改提示词，或重新拍摄。'), ('保存到相册', '保存 AI 图、原图或两张都保留，存入 Pictures / miku camera。')],
         shots=[('outfit', '二次元 · 非公式服'), ('real-outfit', '真人写实 · 非公式服')], sticker='flowers',
         note='应用内实际生成示例，效果随模型与输入变化。'),
    dict(id='download', label='下载应用', tag='09 / DOWNLOAD',
         title='miku camera<br><em>从第一张照片开始</em>',
         description='安装 Android 版，使用普通水印相机；配置自己的 API 服务后，即可开始 AI 创作。',
         features=[('普通模式在本机完成', '取景、水印合成与相册保存都在设备本地处理。'), ('开源项目', '在 GitHub 查看源码与发布版本。')],
         shots=[], sticker='love'),
]

existing = (ROOT/'docs/index.html').read_text(encoding='utf-8')
prefix = existing.split('<main ')[0]
sections = []
for i, item in enumerate(STORY):
    visual = ''.join(
        f'<figure class="shot"><div class="screenshot-frame"><img src="assets/{name}.webp" alt="{label}实机截图" draggable="false"></div><figcaption>{label}</figcaption></figure>'
        for name, label in item['shots'])
    visual_class = 'pair' if len(item['shots']) == 2 else 'single'
    if not item['shots']:
        visual_class = 'brand-ending'
        visual = '<div class="app-sign"><img src="assets/icon.png" alt="miku camera 应用图标"><strong>miku camera</strong><span>水印拍摄 · AI 创作</span></div>'
    features = ''.join(f'<li><b>{a}</b><span>{b}</span></li>' for a,b in item['features'])
    actions = ''
    if item['id'] == 'download':
        actions = '<div class="actions"><a class="button" href="https://github.com/zakee039/miku-camera/releases">下载Android版</a><a class="button" href="https://github.com/zakee039/miku-camera">查看项目</a></div><button class="return-home" type="button">返回首页</button>'
    heading = 'h1' if i == 0 else 'h2'
    sections.append(f'''<section class="slide {'reverse' if i%2 else ''}" id="{item['id']}" aria-label="{i+1} / {len(STORY)} {item['label']}">
  <div class="panel">
    <div class="visual {visual_class}">{visual}</div>
    <div class="copy"><div class="eyebrow">{item['tag']}</div><{heading}>{item['title']}</{heading}><p class="description">{item['description']}</p><ul class="features">{features}</ul>{actions}<p class="note">{item.get('note','')}</p><img class="sticker" src="assets/{item['sticker']}.webp" alt="Miku 表情贴纸" draggable="false"></div>
  </div>
</section>''')
(ROOT/'docs/index.html').write_text(prefix + '<main id="viewport" tabindex="0" aria-label="功能介绍，点击下一页，左右方向键切换页面"><div id="track">\n' + '\n'.join(sections) + '\n</div></main><script src="site.js"></script></body></html>\n', encoding='utf-8')
sources = json.loads((ROOT/'宣传/asset-sources.json').read_text(encoding='utf-8'))
coverage = [{'page': i+1, 'section': s['id'], 'title': s['label'], 'screenshots': [{'asset': name+'.webp', 'source': sources[name+'.webp'], 'caption': label} for name,label in s['shots']]} for i,s in enumerate(STORY)]
used = [shot['source'] for page in coverage for shot in page['screenshots']]
originals = [str(p.relative_to(ROOT)) for p in sorted((ROOT/'设计/miku_camera_screenshot').glob('*.jpg'))]
assert used == originals, 'Every source screenshot must appear once, in original chronological order.'
(ROOT/'宣传/web-story.json').write_text(json.dumps(coverage, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'{len(STORY)} pages; {len(used)} source screenshots in order, no duplicates or omissions.')
