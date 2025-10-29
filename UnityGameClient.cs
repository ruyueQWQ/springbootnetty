using UnityEngine;
using System;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Collections.Generic;

/// <summary>
/// Unity游戏客户端
/// 用于连接游戏服务器并支持键盘操作
/// </summary>
public class UnityGameClient : MonoBehaviour
{
    private TcpClient tcpClient;
    private NetworkStream networkStream;
    private Thread receiveThread;
    private bool connected = false;
    private string host = "localhost";
    private int port = 9090;
    
    // 存储接收到的消息
    private Queue<string> messageQueue = new Queue<string>();
    private object queueLock = new object();
    
    // UI文本组件引用
    public UnityEngine.UI.Text messageText;
    public UnityEngine.UI.InputField inputField;
    
    void Start()
    {
        // 连接到服务器
        ConnectToServer();
        
        // 初始化UI
        if (messageText != null)
        {
            messageText.text = "游戏客户端启动中...\n";
        }
    }
    
    void Update()
    {
        // 处理键盘输入
        HandleKeyboardInput();
        
        // 处理接收到的消息
        ProcessMessages();
    }
    
    void OnDestroy()
    {
        // 断开连接并清理资源
        Disconnect();
    }
    
    /// <summary>
    /// 连接到游戏服务器
    /// </summary>
    private void ConnectToServer()
    {
        try
        {
            tcpClient = new TcpClient();
            tcpClient.ConnectAsync(host, port).ContinueWith(task =>
            {
                if (task.IsFaulted)
                {
                    Debug.LogError("连接服务器失败: " + task.Exception?.Message);
                    AddMessageToQueue("无法连接到服务器: " + task.Exception?.Message + "\n");
                    return;
                }
                
                connected = true;
                networkStream = tcpClient.GetStream();
                
                AddMessageToQueue("成功连接到服务器\n");
                AddMessageToQueue("===== 游戏客户端 =====\n");
                AddMessageToQueue("已连接到游戏服务器。输入 /help 查看可用命令。\n");
                AddMessageToQueue("键盘操作: W/A/S/D移动, 空格键发送消息, ESC退出\n");
                AddMessageToQueue("======================\n");
                
                // 启动接收线程
                receiveThread = new Thread(ReceiveMessages);
                receiveThread.IsBackground = true;
                receiveThread.Start();
            });
        }
        catch (Exception ex)
        {
            Debug.LogError("连接过程中发生错误: " + ex.Message);
            AddMessageToQueue("连接错误: " + ex.Message + "\n");
        }
    }
    
    /// <summary>
    /// 接收来自服务器的消息
    /// </summary>
    private void ReceiveMessages()
    {
        byte[] buffer = new byte[4096];
        StringBuilder messageBuilder = new StringBuilder();
        
        try
        {
            while (connected && tcpClient.Connected)
            {
                int bytesRead = networkStream.Read(buffer, 0, buffer.Length);
                if (bytesRead == 0)
                {
                    // 连接关闭
                    break;
                }
                
                string receivedData = Encoding.UTF8.GetString(buffer, 0, bytesRead);
                messageBuilder.Append(receivedData);
                
                // 处理完整的消息行
                ProcessMessageLines(messageBuilder);
            }
        }
        catch (Exception ex)
        {
            if (connected)
            {
                Debug.LogError("接收消息时出错: " + ex.Message);
                AddMessageToQueue("接收错误: " + ex.Message + "\n");
            }
        }
        finally
        {
            Disconnect();
            AddMessageToQueue("服务器连接已关闭\n");
        }
    }
    
    /// <summary>
    /// 处理消息行
    /// </summary>
    private void ProcessMessageLines(StringBuilder messageBuilder)
    {
        string message = messageBuilder.ToString();
        int newlineIndex;
        
        while ((newlineIndex = message.IndexOf('\n')) != -1)
        {
            string line = message.Substring(0, newlineIndex + 1);
            AddMessageToQueue(line);
            message = message.Substring(newlineIndex + 1);
        }
        
        messageBuilder.Clear();
        messageBuilder.Append(message);
    }
    
    /// <summary>
    /// 发送命令到服务器
    /// </summary>
    private void SendCommand(string command)
    {
        if (!connected || tcpClient == null || !tcpClient.Connected || networkStream == null)
        {
            AddMessageToQueue("未连接到服务器\n");
            return;
        }
        
        try
        {
            byte[] data = Encoding.UTF8.GetBytes(command + "\n");
            networkStream.Write(data, 0, data.Length);
            
            // 如果是退出命令，关闭连接
            if (command.Equals("/quit", StringComparison.OrdinalIgnoreCase))
            {
                Thread.Sleep(500); // 给服务器一些时间处理退出命令
                Disconnect();
            }
        }
        catch (Exception ex)
        {
            Debug.LogError("发送命令时出错: " + ex.Message);
            AddMessageToQueue("发送失败: " + ex.Message + "\n");
            Disconnect();
        }
    }
    
    /// <summary>
    /// 断开与服务器的连接
    /// </summary>
    private void Disconnect()
    {
        if (!connected)
            return;
        
        connected = false;
        
        try
        {
            if (networkStream != null)
            {
                networkStream.Close();
                networkStream = null;
            }
            
            if (tcpClient != null)
            {
                tcpClient.Close();
                tcpClient = null;
            }
            
            if (receiveThread != null && receiveThread.IsAlive)
            {
                receiveThread.Join(1000); // 等待线程结束
                receiveThread = null;
            }
        }
        catch (Exception ex)
        {
            Debug.LogError("断开连接时出错: " + ex.Message);
        }
    }
    
    /// <summary>
    /// 处理键盘输入
    /// </summary>
    private void HandleKeyboardInput()
    {
        // 移动命令
        if (Input.GetKeyDown(KeyCode.W))
        {
            SendCommand("/move up");
            AddMessageToQueue("发送: 向上移动\n");
        }
        else if (Input.GetKeyDown(KeyCode.S))
        {
            SendCommand("/move down");
            AddMessageToQueue("发送: 向下移动\n");
        }
        else if (Input.GetKeyDown(KeyCode.A))
        {
            SendCommand("/move left");
            AddMessageToQueue("发送: 向左移动\n");
        }
        else if (Input.GetKeyDown(KeyCode.D))
        {
            SendCommand("/move right");
            AddMessageToQueue("发送: 向右移动\n");
        }
        // 发送输入框消息
        else if (Input.GetKeyDown(KeyCode.Space) && inputField != null && !string.IsNullOrEmpty(inputField.text))
        {
            SendCommand(inputField.text);
            inputField.text = "";
        }
        // 退出游戏
        else if (Input.GetKeyDown(KeyCode.Escape))
        {
            SendCommand("/quit");
            AddMessageToQueue("正在退出...\n");
            // 在Unity中，这里可以添加Application.Quit()
        }
    }
    
    /// <summary>
    /// 添加消息到队列
    /// </summary>
    private void AddMessageToQueue(string message)
    {
        lock (queueLock)
        {
            messageQueue.Enqueue(message);
        }
    }
    
    /// <summary>
    /// 处理队列中的消息
    /// </summary>
    private void ProcessMessages()
    {
        lock (queueLock)
        {
            while (messageQueue.Count > 0)
            {
                string message = messageQueue.Dequeue();
                if (messageText != null)
                {
                    // 限制文本长度，避免UI性能问题
                    if (messageText.text.Length > 10000)
                    {
                        messageText.text = messageText.text.Substring(5000);
                    }
                    messageText.text += message;
                    
                    // 滚动到底部
                    UnityEngine.UI.ScrollRect scrollRect = messageText.GetComponentInParent<UnityEngine.UI.ScrollRect>();
                    if (scrollRect != null)
                    {
                        scrollRect.verticalNormalizedPosition = 0;
                    }
                }
            }
        }
    }
    
    /// <summary>
    /// 从UI输入框发送消息
    /// </summary>
    public void SendFromInputField()
    {
        if (inputField != null && !string.IsNullOrEmpty(inputField.text))
        {
            SendCommand(inputField.text);
            inputField.text = "";
        }
    }
}