package com.cc.ccojsandbox.sandbox;

import com.cc.ccojsandbox.model.ExecuteCodeRequest;
import com.cc.ccojsandbox.model.ExecuteCodeResponse;

/**
 * 沙箱接口
 */
public interface CodeSandbox {

    ExecuteCodeResponse execute(ExecuteCodeRequest request);
}
