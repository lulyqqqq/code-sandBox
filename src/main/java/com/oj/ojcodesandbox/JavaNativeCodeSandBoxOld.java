package com.oj.ojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.oj.ojcodesandbox.model.ExecuteCodeRepose;
import com.oj.ojcodesandbox.model.ExecuteCodeRequest;
import com.oj.ojcodesandbox.model.ExecuteMessage;
import com.oj.ojcodesandbox.model.JudgeInfo;
import com.oj.ojcodesandbox.util.ProcessUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @ClassName: JavaNativeCodeSandBox
 * @author: mafangnian
 * @date: 2023/10/16 15:01
 */
public class JavaNativeCodeSandBoxOld implements CodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "temCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static void main(String[] args) {
        JavaNativeCodeSandBoxOld javaNativeCodeSandBoxOld = new JavaNativeCodeSandBoxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2","1 3"));
        //使用hutool工具包 读取地址文件中的代码
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeRepose executeCodeRepose = javaNativeCodeSandBoxOld.executeCode(executeCodeRequest);
        System.out.println(executeCodeRepose);
    }
    @Override
    public ExecuteCodeRepose executeCode(ExecuteCodeRequest executeCodeRequest) {
        //使用默认权限管理器
//        System.setSecurityManager(new DefaultSecurityManager());

        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();

        //获取 用户的文件路径
        String userDir = System.getProperty("user.dir");
        System.out.println(userDir);
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
//            执行编译命令
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            //获取编译时，cmd中输出的信息
            ExecuteMessage message = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(message);

        } catch (IOException e) {
            return   getErrorResponse(e);
        }
        //3.执行代码，得到输出结果。
        //循环执行每个输入用例
        List<ExecuteMessage> executeMessageList=new ArrayList<>();
        for (String inputArgs : inputList) {

            String runCmd=String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s",userCodeParentPath,inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage runMessage = ProcessUtil.runProcessAndGetMessage(runProcess, "运行");
                executeMessageList.add(runMessage);
                System.out.println(runMessage);
            } catch (Exception e) {
                return   getErrorResponse(e);
            }
        }
        //4.收集整理输出结果
        ExecuteCodeRepose executeCodeRepose = new ExecuteCodeRepose();
        List<String> outputList = new ArrayList<>();
        //取用时最大值，便于判断是否超时
        long maxTime=0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)){
                //错误执行信息
                executeCodeRepose.setMessage(errorMessage);
                //执行中存在错误
                executeCodeRepose.setStatus(3);
                break;
            }
            //正确的成功信息
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time!=null){
                //取出来的时间进行比较 取最大的那个
                maxTime=Math.max(maxTime,time);
            }
        }
        //正常运行完成
        if (outputList.size()==executeMessageList.size()){
            executeCodeRepose.setStatus(1);
        }
        executeCodeRepose.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
//        Message在判题服务中填写
//        judgeInfo.setMessage();
        //要借助第三方库来获取内存占用，非常麻烦，此处不做实现
//        judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);
        executeCodeRepose.setJudgeInfo(judgeInfo);
        //5.文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        //6.错误处理


        return executeCodeRepose;
    }



    /**
     * 获取错误响应
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
