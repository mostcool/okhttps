---
home: true
heroImage: /logo.png
heroText: OkHttps V2
tagline: 强大轻量 且 前后端通用的 HTTP 客户端，同时支持 WebSocket 以及 Stomp 协议
actionText: 极速上手 →
actionLink: /v2/getstart.html
features:
- title: 轻量纯粹优雅
  details: OkHttps 非常轻量，体积仅是 Retrofit 的一半不到，并且不依赖于特定平台，API 语义简洁舒适。
- title: 开箱即用的功能
  details: 异步预处理器、回调执行器、全局监听器、回调阻断机制、文件上传下载、过程控制、进度监听。
- title: 更多实用特性
  details: URL 占位符、Lambda 回调、JSON自动封装解析、OkHttp 的特性：拦截器、连接池、CookieJar 等。

footer: Apache Licensed | Copyright © 2020-present Troy Zhou
---

### <center> 如艺术一般优雅，像 1、2、3 一样简单 </center>

```java
// 构建实例
HTTP http = HTTP.builder()
        .baseUrl("http://api.example.com")
        .addMsgConvertor(new GsonMsgConvertor());
        .build();

// 同步 HTTP
List<User> users = http.sync("/users") 
        .get()                          // GET请求
        .getBody()                      // 响应报文体
        .toList(User.class);            // 自动反序列化 List 

// 异步 HTTP
http.async("/users/1")
        .setOnResponse((HttpResult res) -> {
            // // 自动反序列化 Bean 
            User user = res.getBody().toBean(User.class);
        })
        .get();                         // GET请求

// WebSocket
http.webSocket("/my-websocket") 
        .setOnMessage((WebSocket ws, Message msg) -> {
            // 从服务器接收消息
            Chat chat = msg.toBean(Chat.class);
            // 向服务器发送消息
            ws.send(chat); 
        })
        .listen();                     // 启动监听

// Stomp 协议
Stomp.over(http.webSocket("wss://...").heatbeat(20, 20))
    .topic("/my-topic", (Message msg) -> {
        // 收到主题消息
        String payload = msg.getPayload();
    })
    .connect();                        // 连接 Stomp 服务
```

<br/>

### <center> 有问必答微信交流群 </center>

<center> <img src="/wx_discuss.png" width = "800" /> </center>

<br/>

<center> 
由于近期交流群的二维码被爬，扫码入群方式已被关闭
<br/>
库的使用上若有疑问，可先加微信【18556739726】（请备注 OkHttps）再入群交流
</center>

<br/>

### [<center> 了解更多 </center>](/v2/)

<br/>