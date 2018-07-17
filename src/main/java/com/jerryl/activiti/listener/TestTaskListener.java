package com.jerryl.activiti.listener;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;

/**
 *
 * @author yuz
 * @date 2018/7/17
 */
public class TestTaskListener implements TaskListener {
    @Override
    public void notify(DelegateTask delegateTask) {
        System.out.println("delegateTask:");
        System.out.println(delegateTask);
        System.out.println(delegateTask.getAssignee());
        System.out.println(delegateTask.getCandidates());
        System.out.println("finished");
    }
}
