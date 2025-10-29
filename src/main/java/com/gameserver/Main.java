package com.gameserver;

import io.vertx.core.Vertx;
import io.vertx.core.DeploymentOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏服务器主类
 * 负责启动Vert.x实例和部署GameServerVerticle
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // 创建Vert.x实例
        Vertx vertx = Vertx.vertx();

        // 设置部署选项
        DeploymentOptions options = new DeploymentOptions();

        // 部署GameServerVerticle
        vertx.deployVerticle(GameServerVerticle.class.getName(), options, res -> {
            if (res.succeeded()) {
                logger.info("游戏服务器启动成功，部署ID: {}", res.result());
                // 注册关闭钩子，优雅关闭
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    logger.info("正在关闭游戏服务器...");
                    vertx.close();
                    logger.info("游戏服务器已关闭");
                }));
            } else {
                logger.error("游戏服务器启动失败", res.cause());
            }
        });
    }
}