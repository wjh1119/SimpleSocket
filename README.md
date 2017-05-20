# SimpleSocket
一个简单的使用Socket的例子
## 介绍
- 开启serverThread作为服务器线程，监听信息。
- 当button被点击时，实例化客户端线程LoginThread并发送账号密码。
- 服务器端获取账号密码后对其验证并发送结果给客户端。
- 工作者线程与UI线程使用Handler进行通信。
