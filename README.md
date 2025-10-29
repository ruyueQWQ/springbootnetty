# Vert.x TCP 游戏服务器

这是一个基于Vert.x框架实现的TCP游戏服务器，提供了游戏服务器的基础功能，包括玩家连接管理、消息处理、聊天系统等。

## 功能特性

- **TCP连接管理**：处理玩家的连接、断开和异常情况
- **玩家管理系统**：维护在线玩家列表，支持玩家改名和状态管理
- **消息处理机制**：模块化的消息处理系统，支持各种游戏指令
- **聊天功能**：支持玩家之间的实时聊天
- **位置同步**：支持玩家位置更新和广播
- **玩家活动**：支持玩家执行各种游戏动作
- **超时检测**：自动检测并清理不活跃的玩家连接
- **HTTP API**：提供服务器状态查询接口
- **日志系统**：详细的日志记录，便于调试和监控

## 技术栈

- **Java 11**：主要开发语言
- **Vert.x**：异步事件驱动的应用框架
- **Maven**：项目构建和依赖管理
- **SLF4J + Logback**：日志记录

## 项目结构

```
src/
├── main/
│   ├── java/com/gameserver/
│   │   ├── Main.java              # 应用程序入口
│   │   ├── GameServerVerticle.java # 游戏服务器核心类
│   │   ├── Player.java            # 玩家类
│   │   ├── MessageHandler.java    # 消息处理器
│   │   └── client/
│   │       └── GameClient.java    # 测试客户端
│   └── resources/
│       └── logback.xml            # 日志配置
├── pom.xml                        # Maven配置
└── README.md                      # 项目说明文档
```

## 快速开始

### 编译和运行

1. 确保已安装JDK 11或更高版本
2. 确保已安装Maven
3. 编译项目：
   ```
   mvn clean package
   ```
4. 运行服务器：
   ```
   java -jar target/vertx-game-server-1.0-SNAPSHOT.jar
   ```

### 连接测试

可以使用提供的GameClient类进行测试：

```
java -cp target/vertx-game-server-1.0-SNAPSHOT.jar com.gameserver.client.GameClient
```

也可以使用telnet或其他TCP客户端工具连接到服务器（端口8080）。

## 命令说明

### 客户端命令

- `NAME:名字` - 设置玩家名字
- `CHAT:消息内容` - 发送聊天消息
- `LIST` - 查看在线玩家列表
- `MOVE:x,y` - 移动到指定位置（x和y为坐标）
- `ACTION:动作` - 执行游戏动作（支持ATTACK和COLLECT）
- `EXIT` - 退出游戏

### 消息格式

服务器和客户端之间使用文本格式的消息，以冒号分隔命令类型和参数。例如：

- 服务器消息：`WELCOME:player-id`
- 聊天消息：`CHAT:player-name:消息内容`
- 玩家列表：`PLAYER_LIST:id1:name1:level1,id2:name2:level2`

## HTTP API

服务器提供了简单的HTTP API用于监控：

- `GET http://localhost:8081/status` - 获取服务器状态信息
- `GET http://localhost:8081/players` - 获取在线玩家列表

## 扩展指南

### 添加新的消息类型

1. 在`MessageHandler`类的`registerProcessors`方法中注册新的处理器
2. 实现对应的消息处理方法
3. 更新客户端命令列表

### 增强游戏功能

1. 在`Player`类中添加更多游戏相关的属性和方法
2. 在`MessageHandler`中实现对应的业务逻辑
3. 考虑添加游戏房间、匹配系统等高级功能

### 性能优化

- 对于大型游戏，考虑使用二进制协议替代文本协议
- 实现消息压缩
- 优化玩家状态同步机制
- 考虑使用分布式架构扩展

## 注意事项

- 这是一个基础版本的游戏服务器，实际游戏开发中需要根据具体需求进行扩展
- 生产环境中应该添加更多的安全措施，如消息验证、速率限制等
- 考虑添加持久化存储功能，保存玩家数据
- 对于大型多人游戏，可能需要考虑使用更复杂的架构，如分片、负载均衡等

## 许可证

MIT