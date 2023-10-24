package com.oj.ojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.oj.ojcodesandbox.model.ExecuteCodeRepose;
import com.oj.ojcodesandbox.model.ExecuteCodeRequest;
import com.oj.ojcodesandbox.model.ExecuteMessage;
import com.oj.ojcodesandbox.model.JudgeInfo;
import com.oj.ojcodesandbox.util.ProcessUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName: JavaNativeCodeSandBox
 * @author: mafangnian
 * @date: 2023/10/16 15:01
 */
public class JavaDockerCodeSandBoxOld extends JavaCodeSandboxTemplate {

    /**
     * 用户代码存放文件夹 会删除
     */
    private static final String GLOBAL_CODE_DIR_NAME = "temCode";
    /**
     * 全局Java执行的类
     */
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    /**
     * 定义是否是第一次拉取镜像
     */
    public static final Boolean FIRST_INIT = true;

    public static final Long TIME_OUT = 5000L;
    public static void main(String[] args) {
        JavaDockerCodeSandBoxOld javaNativeCodeSandBox = new JavaDockerCodeSandBoxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2","1 3"));
        //使用hutool工具包 读取地址文件中的代码
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeRepose executeCodeRepose = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeRepose);
    }

    /**
     * 操作步骤
     * 1.把用户的代码保存为文件
     * 2.编译代码，得到class文件
     * 3.把编译好的文件上传到容器环境内
     * 4.在容器内执行代码，得到输出结果
     * 5.收集整理输出结果
     * 6.文件清理，释放空间
     * 7.错误处理，提升程序健壮性
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeRepose executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();

        //1. 获取用户的文件路径 把用户的代码保存为文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在,没有则新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        //2.编译代码，得到Class文件
        String compileCmd=String.format("javac -encoding utf-8 %s",userCodeFile.getAbsolutePath());
        //java可以使用这个来执行 命令行的命令 需要出来报错
        try {
            //执行编译命令
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            //获取编译时，cmd中输出的信息
            ExecuteMessage message = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(message);

        } catch (IOException e) {
            return   getErrorResponse(e);
        }
        //3.创建容器把文件复制到容器内
        //获取默认的Docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        //拉取java8镜像
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {

            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            //回调函数 查询输出的信息
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像:" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();  //阻塞 直到下载完成才进行下一步

            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("下载完成");

        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        //设置内存
        hostConfig.withMemory(100 * 1000 * 1000L);
        //设置交换区的大小，
        hostConfig.withMemorySwap(0L);
        //设置cpu
        hostConfig.withCpuCount(1L);
        //允许你限制进程可以执行的系统调用
//      hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + profileConfig));
        //容器挂载目录
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                //禁用网络
                .withNetworkDisabled(true)
                //禁止在root目录写文件
                .withReadonlyRootfs(true)
                //开启交互的容器
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                //持续的执行容器
                .withTty(true)
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        //启动容器
        dockerClient.startContainerCmd(containerId).exec();

        //docker exec 容器name java -cp /app Main
        //进入容器执行命令
        //执行命令获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            //计时
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    //执行运行命令
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);
            //创建执行程序的信息存放值
            ExecuteMessage executeMessage = new ExecuteMessage();
            // 存放代码执行完毕之后的输出信息
            final String[] message = {null};
            // 存放代码执行的错误信息
            final String[] errorMessage = {null};
            long time = 0L;
            //判断是否超时,默认就是超时
            final boolean[] timeout = {true};
            String execId = execCreateCmdResponse.getId();
            //回调函数
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                //正常执行完后会执行这个方法
                @Override
                public void onComplete() {
                    //如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    //并且通过 StreamType 来区分标准输出和错误输出。
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        //获得错误信息
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果" + errorMessage[0]);
                    } else {
                        //获得代码正确执行的结果
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果" + message[0]);
                    }
                    super.onNext(frame);
                }
            };
            //获取占用内存
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    Long usageMemory = statistics.getMemoryStats().getUsage();
                    System.out.println("内存占用:" +usageMemory);
                    if (usageMemory!=null){
                        maxMemory[0] = Math.max(usageMemory, maxMemory[0]);

                    }
                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });


            //先启动监控 监控内存等信息
            statsCmd.exec(statisticsResultCallback);
            statsCmd.close();
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);  //设置超时时间
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("执行异常");
                throw new RuntimeException(e);
            }

            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            //将多例输入的输出结果放入数组中
            executeMessageList.add(executeMessage);
        }
        //删除容器
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
        System.out.println("删除成功");

        ExecuteCodeRepose executeCodeRepose = new ExecuteCodeRepose();
        return executeCodeRepose;
    }



    /**
     * 获取错误响应
     *
     * @param e
     * @return
     */
    private ExecuteCodeRepose getErrorResponse(Throwable e) {
        ExecuteCodeRepose executeCodeResponse = new ExecuteCodeRepose();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
