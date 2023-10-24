package com.oj.ojcodesandbox;

import com.oj.ojcodesandbox.model.ExecuteCodeRepose;
import com.oj.ojcodesandbox.model.ExecuteCodeRequest;

/**
 * 代码沙箱 运行代码获得返回值
 * 接受代码==> 编译代码(javac) =>执行代码
 */
public interface CodeSandBox {

    ExecuteCodeRepose executeCode(ExecuteCodeRequest executeCodeRequest);
}
