package com.gameserver;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 消息处理器
 * 负责处理游戏中的各种消息类型
 */
public class MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);
    private final Vertx vertx;
    private final Map<String, Player> players;

    public MessageHandler(Vertx vertx, Map<String, Player> players) {
        this.vertx = vertx;
        this.players = players;
    }

    /**
     * 处理接收到的消息
     * @param playerId 玩家ID
     * @param message 消息内容
     */
    public void handleMessage(String playerId, String message) {
        try {
            Player player = players.get(playerId);
            
            if (player == null) {
                logger.warn("尝试处理不存在玩家的消息: {}", playerId);
                return;
            }
            
            // 更新玩家活动时间
            player.updateActivity();
            
            // 处理命令
            if (message.startsWith("/")) {
                handleCommand(playerId, message);
            } else {
                // 处理聊天消息
                broadcastToAll(player.getName() != null ? player.getName() : playerId, message);
            }
        } catch (Exception e) {
            logger.error("处理消息时出错", e);
        }
    }

    /**
     * 处理命令
     * @param playerId 玩家ID
     * @param command 命令字符串
     */
    private void handleCommand(String playerId, String command) {
        Player player = players.get(playerId);
        if (player == null) return;
        
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        
        switch (cmd) {
            case "/name":
                if (parts.length > 1) {
                    String oldName = player.getName();
                    String newName = parts[1];
                    // 验证昵称长度和内容
                    if (newName.length() > 20) {
                        sendMessage(player.getSocket(), "系统", "昵称不能超过20个字符");
                        return;
                    }
                    if (!newName.matches("[a-zA-Z0-9_\u4e00-\u9fa5]+") || newName.trim().isEmpty()) {
                        sendMessage(player.getSocket(), "系统", "昵称只能包含字母、数字、下划线和中文");
                        return;
                    }
                    player.setName(newName.trim());
                    sendMessage(player.getSocket(), "系统", "你的昵称已更改为: " + newName.trim());
                    broadcastToAll("系统", (oldName != null ? oldName : playerId) + " 更名为 " + newName.trim());
                } else {
                    sendMessage(player.getSocket(), "系统", "请输入昵称，格式: /name 你的昵称");
                }
                break;
            
            case "/list":
                StringBuilder playerList = new StringBuilder("在线玩家列表:\n");
                for (Map.Entry<String, Player> entry : players.entrySet()) {
                    Player p = entry.getValue();
                    playerList.append("- ")
                            .append(p.getName() != null ? p.getName() : entry.getKey())
                            .append(entry.getKey().equals(playerId) ? " (你)" : "")
                            .append("\n");
                }
                sendMessage(player.getSocket(), "系统", playerList.toString());
                break;
            
            case "/help":
                String helpMsg = "可用命令:\n" +
                        "/name 昵称 - 设置你的昵称\n" +
                        "/list - 查看在线玩家列表\n" +
                        "/help - 查看帮助信息\n" +
                        "/quit - 退出游戏\n" +
                        "/ping - 测试连接\n" +
                        "直接输入消息发送聊天";
                sendMessage(player.getSocket(), "系统", helpMsg);
                break;
            
            case "/quit":
                sendMessage(player.getSocket(), "系统", "再见！");
                try {
                    player.getSocket().close();
                } catch (Exception e) {
                    logger.error("关闭连接时出错", e);
                }
                break;
            
            case "/ping":
                sendMessage(player.getSocket(), "系统", "pong");
                break;
            
            case "/info":
                sendMessage(player.getSocket(), "系统", "服务器信息：\n" +
                        "- 在线人数: " + players.size() + "\n" +
                        "- 你的ID: " + playerId + "\n" +
                        "- 你的昵称: " + (player.getName() != null ? player.getName() : "未设置"));
                break;
            
            default:
                sendMessage(player.getSocket(), "系统", "未知命令: " + cmd + "，输入 /help 查看可用命令");
                break;
        }
    }

    /**
     * 发送消息给指定玩家
     * @param playerId 玩家ID
     * @param message 消息内容
     */
    public void sendMessage(String playerId, String message) {
        Player player = players.get(playerId);
        if (player != null) {
            sendMessage(player.getSocket(), "系统", message);
        }
    }

    /**
     * 发送消息给指定的Socket
     * @param socket 目标Socket
     * @param sender 发送者
     * @param content 消息内容
     */
    private void sendMessage(NetSocket socket, String sender, String content) {
        // 检查连接状态和写入队列状态
        if (socket != null && !socket.writeQueueFull()) {
            try {
                String fullMessage = "[" + sender + "]: " + content + "\n";
                socket.write(Buffer.buffer(fullMessage, "UTF-8"));
            } catch (Exception e) {
                logger.error("发送消息失败", e);
            }
        }
    }

    /**
     * 广播消息给所有玩家
     * @param sender 发送者
     * @param content 消息内容
     */
    public void broadcastToAll(String sender, String content) {
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            Player player = entry.getValue();
            sendMessage(player.getSocket(), sender, content);
        }
    }

    /**
     * 广播消息给所有玩家，但排除指定玩家
     * @param sender 发送者
     * @param content 消息内容
     * @param excludePlayerId 排除的玩家ID
     */
    public void broadcastToAll(String sender, String content, String excludePlayerId) {
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            String playerId = entry.getKey();
            if (!playerId.equals(excludePlayerId)) {
                Player player = entry.getValue();
                sendMessage(player.getSocket(), sender, content);
            }
        }
    }
}