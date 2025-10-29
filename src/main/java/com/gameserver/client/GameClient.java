package com.gameserver.client;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * 游戏客户端
 * 用于测试游戏服务器功能
 */
public class GameClient {

    private static final Logger logger = LoggerFactory.getLogger(GameClient.class);
    private Vertx vertx;
    private NetClient client;
    private NetSocket socket;
    private boolean connected = false;
    private String host = "localhost";
    private int port = 9090;

    public GameClient() {
        this.vertx = Vertx.vertx();
        this.client = vertx.createNetClient();
    }

    /**
     * 连接到游戏服务器
     */
    public void connect() {
        logger.info("正在连接到服务器 {}:{}", host, port);
        
        client.connect(port, host, res -> {
            if (res.succeeded()) {
                logger.info("成功连接到服务器");
                socket = res.result();
                connected = true;
                
                // 处理从服务器接收的消息
                socket.handler(buffer -> {
                    try {
                        String message = buffer.toString("UTF-8");
                        System.out.print(message);
                    } catch (Exception e) {
                        logger.error("处理消息时出错", e);
                    }
                });
                
                // 处理连接关闭
                socket.closeHandler(v -> {
                    logger.info("与服务器的连接已关闭");
                    connected = false;
                    System.out.println("\n服务器连接已关闭，程序即将退出...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.exit(0);
                });
                
                // 处理连接异常
                socket.exceptionHandler(e -> {
                    logger.error("连接发生错误", e);
                    connected = false;
                    System.err.println("\n连接错误: " + e.getMessage());
                    System.err.println("程序即将退出...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    System.exit(1);
                });
                
                // 启动命令输入线程
                startCommandInput();
            } else {
                logger.error("连接服务器失败", res.cause());
                System.err.println("无法连接到服务器: " + res.cause().getMessage());
                System.err.println("请检查服务器是否运行并尝试重新启动客户端。");
                System.exit(1);
            }
        });
    }

    /**
     * 启动命令输入线程
     */
    private void startCommandInput() {
        // 在单独的线程中处理命令输入，避免阻塞Vert.x事件循环
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("===== 游戏客户端 =====");
            System.out.println("已连接到游戏服务器。输入 /help 查看可用命令。");
            System.out.println("======================\n");
            
            try {
                while (connected) {
                    if (scanner.hasNextLine()) {
                        String command = scanner.nextLine();
                        
                        // 发送命令到服务器
                        sendCommand(command);
                        
                        // 如果是退出命令，关闭连接
                        if (command.equals("/quit")) {
                            try {
                                Thread.sleep(500); // 给服务器一些时间处理退出命令
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            disconnect();
                            break;
                        }
                    } else {
                        // 如果没有输入，短暂休眠避免CPU占用过高
                        Thread.sleep(100);
                    }
                }
            } catch (Exception e) {
                logger.error("命令处理线程发生错误", e);
            } finally {
                scanner.close();
            }
        }).start();
    }

    /**
     * 发送命令到服务器
     * @param command 要发送的命令
     */
    private void sendCommand(String command) {
        if (socket != null) {
            try {
                socket.write(Buffer.buffer(command + "\n", "UTF-8"));
            } catch (Exception e) {
                System.err.println("发送命令失败: " + e.getMessage());
                disconnect();
            }
        } else {
            System.out.println("未连接到服务器");
        }
    }

    /**
     * 断开与服务器的连接
     */
    public void disconnect() {
        try {
            if (connected && socket != null) {
                socket.close();
                connected = false;
            }
        } catch (Exception e) {
            logger.error("关闭连接时出错", e);
        }
        
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            logger.error("关闭客户端时出错", e);
        }
        
        try {
            if (vertx != null) {
                vertx.close();
            }
        } catch (Exception e) {
            logger.error("关闭Vert.x时出错", e);
        }
        
        logger.info("客户端已关闭");
    }

    /**
     * 主方法，启动客户端
     */
    public static void main(String[] args) {
        GameClient client = new GameClient();
        
        // 解析命令行参数
        if (args.length > 0) {
            client.host = args[0];
        }
        if (args.length > 1) {
            try {
                client.port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                logger.error("端口号格式错误", e);
                System.err.println("警告: 端口号格式错误，使用默认端口 9090");
            }
        }
        
        // 连接到服务器
        client.connect();
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.disconnect();
        }));
    }
}