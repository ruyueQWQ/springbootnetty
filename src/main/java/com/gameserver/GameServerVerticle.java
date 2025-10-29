package com.gameserver;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 游戏服务器Verticle
 * 负责处理TCP连接、玩家管理和HTTP API
 */
public class GameServerVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(GameServerVerticle.class);
    
    // TCP服务器配置
    private static final int TCP_PORT = 9090;
    private static final String TCP_HOST = "0.0.0.0";
    
    // 存储所有连接的玩家
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private NetServer server;
    private MessageHandler messageHandler;
    private long timeoutCheckerId;

    @Override
    public void start() {
        // 初始化消息处理器
        messageHandler = new MessageHandler(vertx, players);
        
        // 创建TCP服务器
        server = vertx.createNetServer();
        
        // 处理新的连接
        server.connectHandler(this::handleNewConnection);
        
        // 启动服务器
        server.listen(TCP_PORT, TCP_HOST, result -> {
            if (result.succeeded()) {
                logger.info("游戏服务器已启动，监听端口: {}", TCP_PORT);
                
                // 启动HTTP API服务（可选，用于管理）
                startHttpApi();
                
                // 启动玩家超时检查定时器
                startTimeoutChecker();
            } else {
                logger.error("游戏服务器启动失败", result.cause());
            }
        });
    }

    /**
     * 处理新的TCP连接
     */
    private void handleNewConnection(NetSocket socket) {
        // 生成唯一的玩家ID
        String playerId = UUID.randomUUID().toString();
        
        // 创建玩家对象
        Player player = new Player(playerId, socket);
        players.put(playerId, player);
        
        logger.info("新玩家连接: {}, 当前在线人数: {}", playerId, players.size());
        
        // 发送欢迎消息
        sendMessage(socket, "系统", "欢迎加入游戏！请使用 /name 命令设置你的昵称");
        
        // 广播玩家加入消息
        broadcastToAll("系统", playerId + " 加入了游戏", playerId);
        
        // 处理接收到的数据
        socket.handler(buffer -> {
            try {
                String message = buffer.toString("UTF-8").trim();
                logger.debug("收到玩家 {} 的消息: {}", playerId, message);
                
                // 更新玩家最后活动时间
                player.updateLastActiveTime();
                
                // 处理消息
                handleMessage(playerId, buffer);
            } catch (Exception e) {
                logger.error("处理玩家消息时出错", e);
                sendMessage(socket, "系统", "消息处理出错，请重试");
            }
        });
        
        // 处理连接关闭
        socket.closeHandler(v -> handleDisconnect(playerId));
        
        // 处理异常
        socket.exceptionHandler(e -> handleException(playerId, e));
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(String playerId, Buffer buffer) {
        // 使用消息处理器处理消息
        try {
            messageHandler.handleMessage(playerId, buffer.toString("UTF-8").trim());
        } catch (Exception e) {
            logger.error("消息处理失败", e);
        }
    }

    /**
     * 处理玩家断开连接
     */
    private void handleDisconnect(String playerId) {
        Player player = players.remove(playerId);
        if (player != null) {
            logger.info("玩家断开连接: {}, 当前在线人数: {}", playerId, players.size());
            // 广播玩家离开消息
            broadcastToAll("系统", player.getName() != null ? player.getName() : playerId + " 离开了游戏");
        }
    }

    /**
     * 处理连接异常
     */
    private void handleException(String playerId, Throwable e) {
        logger.error("玩家 {} 连接异常", playerId, e);
        // 清理断开的连接
        handleDisconnect(playerId);
    }

    /**
     * 启动玩家超时检查定时器
     */
    private void startTimeoutChecker() {
        // 每分钟检查一次玩家活动状态
        timeoutCheckerId = vertx.setPeriodic(TimeUnit.MINUTES.toMillis(1), id -> {
            long now = System.currentTimeMillis();
            long timeoutThreshold = TimeUnit.MINUTES.toMillis(5); // 5分钟超时
            
            for (Map.Entry<String, Player> entry : new ConcurrentHashMap<>(players).entrySet()) {
                String playerId = entry.getKey();
                Player player = entry.getValue();
                
                if (now - player.getLastActiveTime() > timeoutThreshold) {
                    logger.info("玩家 {} 超时，断开连接", playerId);
                    kickPlayer(playerId, "连接超时");
                }
            }
        });
    }

    /**
     * 启动HTTP API服务（可选）
     * 用于管理服务器状态查询等功能
     */
    private void startHttpApi() {
        Router router = Router.router(vertx);
        
        // 获取在线玩家数量
        router.get("/api/players/count").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end("{\"count\": " + players.size() + "}");
        });
        
        // 获取在线玩家列表
        router.get("/api/players").handler(ctx -> {
            StringBuilder playersJson = new StringBuilder("[");
            boolean first = true;
            
            for (Player player : players.values()) {
                if (!first) {
                    playersJson.append(", ");
                } else {
                    first = false;
                }
                playersJson.append("{\"id\": \"")
                        .append(player.getId())
                        .append("\", \"name\": \"")
                        .append(player.getName() != null ? player.getName() : "Unnamed")
                        .append("\"}");
            }
            
            playersJson.append("]");
            
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(playersJson.toString());
        });
        
        // 服务器状态信息
        router.get("/api/status").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end("{\"status\": \"online\", \"players\": " + players.size() + "}");
        });
        
        // 启动HTTP服务器
        vertx.createHttpServer().requestHandler(router).listen(9091, res -> {
            if (res.succeeded()) {
                logger.info("HTTP API服务已启动，监听端口: 8081");
            } else {
                logger.error("HTTP API服务启动失败", res.cause());
            }
        });
        
        // 保持向后兼容性，添加原始API路径
        router.get("/status").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end("{\"onlinePlayers\": " + players.size() + ", \"serverTime\": " + System.currentTimeMillis() + ", \"status\": \"running\"}");
        });
        
        router.get("/players").handler(ctx -> {
            StringBuilder playersJson = new StringBuilder("[");
            boolean first = true;
            
            for (Player player : players.values()) {
                if (!first) {
                    playersJson.append(", ");
                } else {
                    first = false;
                }
                playersJson.append("{\"id\": \"")
                        .append(player.getId())
                        .append("\", \"name\": \"")
                        .append(player.getName() != null ? player.getName() : "Unnamed")
                        .append("\"}");
            }
            
            playersJson.append("]");
            
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(playersJson.toString());
        });
    }

    private void kickPlayer(String playerId, String reason) {
        Player player = players.get(playerId);
        if (player != null) {
            try {
                // 发送踢人原因
                sendMessage(player.getSocket(), "系统", "你被踢出游戏: " + reason);
                // 关闭连接
                player.getSocket().close();
            } catch (Exception e) {
                logger.error("踢人时出错", e);
            }
        }
    }
    
    // 发送消息给指定socket的辅助方法
    private void sendMessage(NetSocket socket, String sender, String content) {
        if (socket != null) {
            try {
                String message = "[" + sender + "]: " + content + "\n";
                socket.write(Buffer.buffer(message, "UTF-8"));
            } catch (Exception e) {
                logger.error("发送消息失败", e);
            }
        }
    }
    
    // 广播消息给所有玩家，但排除指定玩家
    private void broadcastToAll(String sender, String content, String excludePlayerId) {
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            String playerId = entry.getKey();
            if (!playerId.equals(excludePlayerId)) {
                Player player = entry.getValue();
                sendMessage(player.getSocket(), sender, content);
            }
        }
    }
    
    // 广播消息给所有玩家
    private void broadcastToAll(String sender, String content) {
        for (Player player : players.values()) {
            sendMessage(player.getSocket(), sender, content);
        }
    }
    
    @Override
    public void stop() {
        // 取消超时检查器
        if (timeoutCheckerId > 0) {
            vertx.cancelTimer(timeoutCheckerId);
        }
        
        if (server != null) {
            server.close();
        }
        logger.info("游戏服务器已停止");
    }
}