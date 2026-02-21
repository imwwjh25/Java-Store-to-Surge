


# TLS/SSL 通信过程总结

TLS（Transport Layer Security）和其前身SSL（Secure Sockets Layer）是为网络通信提供机密性、完整性和身份验证的协议。其通信过程主要包括两个阶段：

------

## 1. TLS/SSL 握手阶段（Handshake）

**目的**：

- 确定双方能力（支持的协议版本、密码套件等）
- 服务器身份验证（通常通过证书）
- 双方协商会话密钥（用于加密后续通信）
- 交换加密参数，建立安全通信通道

### 主要步骤：

1. **ClientHello（客户端问候）**
   客户端发送支持的 TLS 版本、支持的密码套件列表（加密算法）、随机数（ClientRandom）等信息。
2. **ServerHello（服务器响应）**
   服务器选择协议版本、确定使用的加密套件、发送服务器随机数（ServerRandom）。
3. **服务器发送证书（Certificate）**
   服务器将其公钥证书发给客户端，用于身份验证。
4. **服务器密钥交换（可选，视具体加密套件）**
   如使用 DHE 或 ECDHE 等带前向保密的密钥交换算法，服务器发送相应参数。
5. **服务器请求客户端证书（可选）**
   双向认证时，服务器要求客户端出具证书。
6. **服务器Hello完成（ServerHelloDone）**
   告知客户端服务器端握手消息发送完毕。
7. **客户端验证服务器证书**
   验证证书合法性（CA签名、有效期、撤销等），并根据 ServerRandom、ClientRandom 和服务器公钥，生成预主密钥（Pre-Master Secret）。
8. **客户端发送加密的预主密钥（ClientKeyExchange）**
   通常用服务器公钥加密，将预主密钥发送给服务器。
9. **客户端生成主密钥（Master Secret）**
   服务器和客户端用预主密钥、ClientRandom、ServerRandom均生成同一个主密钥。
10. **客户端发送ChangeCipherSpec消息**
    通知服务器后续消息将使用协商好的加密算法和主密钥进行加密。
11. **客户端发送Finished消息**
    校验握手过程是否完整且未被篡改，消息内容加密。
12. **服务器收到ChangeCipherSpec和Finished消息，回应相同消息**
    服务器也发送 ChangeCipherSpec、Finished消息，完成握手。

------

## 2. TLS/SSL 记录传输阶段（Record Protocol）

- 握手完成后，客户端与服务器使用商定的对称密钥和算法加密后续应用数据。
- 每条消息包括消息认证码（MAC），保证数据完整性和认证。
- 支持压缩（可选）和分块传输。
- 通信期间双方可协商重新密钥以增强安全。