package com.oj.ojcodesandbox;

import com.oj.ojcodesandbox.model.ExecuteCodeRepose;
import com.oj.ojcodesandbox.model.ExecuteCodeRequest;
import org.springframework.stereotype.Component;

/**
 * @ClassName: JavaNativeCodeSandBox
 * @author: mafangnian
 * @date: 2023/10/17 22:03
 */
@Component
public class JavaNativeCodeSandBox extends JavaCodeSandboxTemplate{


    @Override
    public ExecuteCodeRepose executeCode(ExecuteCodeRequest executeCodeRequest){
        return super.executeCode(executeCodeRequest);
    }

}
