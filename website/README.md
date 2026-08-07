# FluxCache 官网

基于 [VitePress](https://vitepress.dev) 的静态站点。

## 目录结构

```
website/
├── .vitepress/
│   ├── config.mjs      # 站点配置（导航、侧边栏、base 路径）
│   ├── dist/           # 构建产物
│   └── theme/          # 自定义主题
├── guide/              # 指南文档（markdown 源文件）
├── public/             # 静态资源（logo 等）
├── index.md            # 首页
└── package.json
```

## 本地预览

```bash
cd website && npm install

# 开发模式（热更新），默认 http://localhost:5173/fluxcache/
cd website && npm run dev

# 生产构建 + 预览一步完成。注意 base 路径为 /fluxcache/，
# 预览地址是 http://localhost:4173/fluxcache/
cd website && npm run start
```

## 文档修改

`guide/` 下的 `.md` 即为页面源码，修改后开发模式下会热更新。
新增页面需同步更新 `.vitepress/config.mjs` 的 `nav` 和 `sidebar`。

## 发布

- CI（`.github/workflows/ci.yml` 的 `website` job）会在每次 push 时自动执行 `npm ci && npm run build`，保证文档可构建。
- 站点 base 路径为 `/fluxcache/`，发布到 GitHub Pages 需部署为 `https://<user>.github.io/fluxcache/`。