package com.jerryl.activiti.service;

import org.activiti.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;

/**
 * @author yuz
 */
@Service("testService")
public class TestService {

    public void test(DelegateExecution execution) {
        System.out.println("test...........,,");
    }
}
