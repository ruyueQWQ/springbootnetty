package com.gameserver;

import io.vertx.core.net.NetSocket;

/**
 * 玩家类
 * 表示游戏中的玩家对象
 */
public class Player {
    private String id;          // 玩家唯一标识符
    private String name;        // 玩家名称
    private NetSocket socket;   // 玩家的网络连接
    private long lastActiveTime; // 最后活动时间

    /**
     * 构造方法
     * @param id 玩家ID
     * @param socket 玩家的网络连接
     */
    public Player(String id, NetSocket socket) {
        this.id = id;
        this.socket = socket;
        this.name = null; // 初始名称为null
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * 更新玩家活动时间
     */
    public void updateActivity() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * 更新玩家最后活动时间（兼容方法）
     */
    public void updateLastActiveTime() {
        updateActivity();
    }

    /**
     * 获取玩家ID
     * @return 玩家ID
     */
    public String getId() {
        return id;
    }

    /**
     * 获取玩家名称
     * @return 玩家名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置玩家名称
     * @param name 玩家名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取玩家的网络连接
     * @return 网络连接
     */
    public NetSocket getSocket() {
        return socket;
    }

    /**
     * 获取最后活动时间
     * @return 最后活动时间戳
     */
    public long getLastActiveTime() {
        return lastActiveTime;
    }

    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", name='" + (name != null ? name : "未设置") + '\'' +
                '}';
    }
}