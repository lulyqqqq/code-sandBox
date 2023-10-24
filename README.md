## 代码沙箱
接受代码==> 编译代码(javac) =>执行代码

根据外部传入的message信息,里面携带了相关的用户代码,输入示例和输出示例和编程语言，目前只实现了Java模板的终端执行Java程序
```java
//输入用例
private List<String> inputList;
//代码
private String code;
//语言
private String language;
```
### 本地实现机制:
分为六大部分
* 1.把用户的代码保存为文件
* 2.编译文件代码，得到class文件
* 3.执行代码，得到输出结果
* 4.收集整理输出结果
* 5.文件清理，释放空间
* 6.错误处理，提升程序健壮性

其中第四部分是读取终端执行Java代码打印出的数据,作为程序输出,将此结果返回给调用沙箱服务方

### 拓展部分-docker实现代码沙箱
使用Java程序操作docker

分为七大部分
* 1.把用户的代码保存为文件
* 2.编译代码，得到class文件
* 3.把编译好的文件上传到容器环境内
* 4.在容器内执行代码，得到输出结果
* 5.收集整理输出结果
* 6.文件清理，释放空间
* 7.错误处理，提升程序健壮性

主要是多出了在docker容器中执行相关命令,获取docker镜像输出结果以及配置docker容器相关参数,防止内存溢出,占有时间过久,间断监控docker运行状态

### 访问
外部程序通过http请求访问判题接口
```shell
http://localhost:8011/executeCode
```
自定义内部鉴权机制,因为是服务间相互调用,鉴权写的相对简单,需要头部传入对于的auth信息才可访问沙箱服务
```java
    //用一个字符串来保证接口调用的安全性，只要对面传入这个字符串，那就代表可以调用
    public static final String AUTH_REQUEST_HEADER = "auth";
    public static final String AUTH_REQUEST_SECRET = "secretKey";
```
