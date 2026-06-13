# NewAPI 冒烟验证

本项目的 NewAPI 冒烟验证只从环境变量读取密钥，不把真实 API Key 写入脚本、文档、测试 fixture、日志或 Git 历史。

## 测试API配置

项目提供测试API配置脚本，用于快速设置测试环境：

```powershell
# 配置测试API环境变量（临时）
.\scripts\test\configure_test_api.ps1

# 配置测试API环境变量（持久化到用户级别）
.\scripts\test\configure_test_api.ps1 -Persistent
```

测试API配置包括：
- 聊天API：https://zzshu.cc
- 图像生成API：https://zzshu.cc/v1/images/generations
- Brave Search API：用于网络搜索功能

## 环境变量

- `NEWAPI_API_KEY`：聊天/模型接口 Key。
- `NEWAPI_IMAGE_KEY`：图片生成接口 Key。
- `BRAVE_SEARCH_API_KEY`：Brave Search API Key。

## 非计费验证

默认只验证模型接口和图片接口的鉴权/请求校验路径，不发起真实图片生成。

```powershell
.\scripts\test\newapi_smoke.ps1 -RequireKeys
```

预期结果：

- `chat_models: ok (HTTP 200)`
- `image_endpoint: auth accepted or request validation reached (...)`

## 图片生成验证

只有明确需要验证真实图片生成时才使用 `-AllowImageGenerate`，该请求可能产生费用。

```powershell
.\scripts\test\newapi_smoke.ps1 -RequireKeys -AllowImageGenerate -ImageModel "<image-model>"
```

## 安全规则

- 不在命令行里直接粘贴真实 Key。
- 不把 Key 写入 `.env` 以外的文件；`.env` 已被 `.gitignore` 忽略。
- 不把完整响应日志提交到仓库。
