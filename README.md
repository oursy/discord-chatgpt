#  Discord ChatGPT Bot

> 在 Discord 中构建 使用 ChatGPT 机器人 


## 功能

- `/chat [message]` 与 ChatGPT 聊天！ 支持流式。
- `/reset-chat`  清除上下文

## 配置

### 创建 Discord Bot 
-  前往 https://discord.com/developers/applications 创建一个 Application 
- 在Application下建立Discord 机器人
- 从机器人设置中获取Token
- 将 Token 储存到 .env 中 `DISCORD_TOKEN`参数中
- 将「MESSAGE CONTENT INTENT」 选项打开
- 通过 OAuth2 URL生成器生成邀请网址将机器人邀请导您的服务器

### 生成 OpenAI token

- 前往 https://platform.openai.com/account/api-keys
- 创建密钥，
- 将密钥  存储到 .env 中 `OPENAI_KEY`参数中





