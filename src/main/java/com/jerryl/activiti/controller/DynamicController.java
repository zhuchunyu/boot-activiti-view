package com.jerryl.activiti.controller;

import com.google.common.collect.Maps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jerryl.activiti.listener.TestTaskListener;
import com.jerryl.activiti.model.ProcessNode;

import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.model.ActivitiListener;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.EndEvent;
import org.activiti.bpmn.model.ExclusiveGateway;
import org.activiti.bpmn.model.ImplementationType;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.IdentityService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.naming.OperationNotSupportedException;

/**
 * Created by jerry on 2018/7/16.
 */
@RestController
@RequestMapping("/dynamic")
public class DynamicController {

    @Autowired
    RepositoryService repositoryService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    IdentityService identityService;
    @Autowired
    TaskService taskService;

    Map<String, Long> newMap(String key, Long value) {
        Map<String, Long> map = Maps.newHashMap();
        map.put(key, value);
        return map;
    }

    @GetMapping("/generate")
    public Map<String, Object> generate() {
        Map<String, Object> result = Maps.newHashMap();

        Map<Integer, String> types = Maps.newHashMap();
        types.put(1, "开始");
        types.put(2, "结束");
        types.put(3, "角色网关");
        types.put(4, "审核");
        types.put(5, "服务");

        List<ProcessNode> nodes = new ArrayList<>();

        ProcessNode nodeStart = new ProcessNode();
        nodeStart.setId(1L);
        nodeStart.setName("开始");
        nodeStart.setType(1);
        nodeStart.setNextNode(2L);
        nodes.add(nodeStart);

        ProcessNode gateway = new ProcessNode();
        gateway.setId(2L);
        gateway.setName("角色网关");
        gateway.setType(3);
        gateway.setConditionType("role");
        gateway.setConditions(Arrays.asList(newMap("101", 5L),
                newMap("201", 4L), newMap("203", 3L)));
        nodes.add(gateway);

        ProcessNode projectNode = new ProcessNode();
        projectNode.setId(3L);
        projectNode.setName("项目管理员审核");
        projectNode.setType(4);
        projectNode.setRoles(Arrays.asList("projet-203", "user-1001"));
        projectNode.setConditionType("whether");
        projectNode.setConditions(Arrays.asList(newMap("pass", 4L), newMap("reject", 6L)));
        nodes.add(projectNode);

        ProcessNode companyNode = new ProcessNode();
        companyNode.setId(4L);
        companyNode.setName("企业管理员审核");
        companyNode.setType(4);
        companyNode.setRoles(Arrays.asList("company-101", "user-1203"));
        companyNode.setConditionType("whether");
        companyNode.setConditions(Arrays.asList(newMap("pass", 5L), newMap("reject", 6L)));
        nodes.add(companyNode);

        ProcessNode openNode = new ProcessNode();
        openNode.setId(5L);
        openNode.setName("开通服务");
        openNode.setType(5);
        openNode.setServices(Arrays.asList(1L, 2L));
        openNode.setNextNode(7L);
        nodes.add(openNode);

        ProcessNode cancelNode = new ProcessNode();
        cancelNode.setId(6L);
        cancelNode.setName("决绝并关闭");
        cancelNode.setType(5);
        cancelNode.setServices(Arrays.asList(3L));
        cancelNode.setNextNode(7L);
        nodes.add(cancelNode);

        ProcessNode nodeEnd = new ProcessNode();
        nodeEnd.setId(7L);
        nodeEnd.setName("开始");
        nodeEnd.setType(2);
        nodes.add(nodeEnd);

        result.put("types", types);
        result.put("nodes", nodes);
        result.put("status", 200);
        return result;
    }

    @GetMapping("/listener")
    public Map<String, Object> listener() {
        Map<String, Object> result = Maps.newHashMap();

        BpmnModel model = new BpmnModel();
        Process process = new Process();
        model.addProcess(process);
        process.setId("one-process");

        process.addFlowElement(createStartEvent());
        UserTask userTask = createUserTask("listener1", "一个任务", "fred,john,jerry");

        List<ActivitiListener> taskListeners = new ArrayList<>();

        ActivitiListener listener = new ActivitiListener();
        // create assignment complete
        listener.setEvent("create");
        listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
        listener.setImplementation(TestTaskListener.class.getName());
        taskListeners.add(listener);
        System.out.println(TestTaskListener.class.getName());

        userTask.setTaskListeners(taskListeners);

        process.addFlowElement(userTask);
        process.addFlowElement(createEndEvent());

        process.addFlowElement(createSequenceFlow("start", "listener1"));
        process.addFlowElement(createSequenceFlow("listener1", "end"));

        new BpmnAutoLayout(model).execute();

        // 3. Deploy the process to the engine
        Deployment deployment = repositoryService.createDeployment()
                .addBpmnModel("listener-model.bpmn", model).name("Dynamic process deployment").deploy();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("one-process", UUID.randomUUID().toString());
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId()).list();
        System.out.println(tasks.size());

        return result;
    }

    @GetMapping("/process")
    public Map<String, Object> dynamuc() {
        Map<String, Object> result = Maps.newHashMap();

        BpmnModel model = new BpmnModel();
        Process process = new Process();
        model.addProcess(process);
        process.setId("my-process");

        process.addFlowElement(createStartEvent());
        process.addFlowElement(createUserTask("task1", "First task", "fred"));
        process.addFlowElement(createExclusiveGateway("gateway1", "gateway"));
        process.addFlowElement(createUserTask("task2", "Second task", "john"));
        process.addFlowElement(createUserTask("task3", "Third task", "jack"));
        process.addFlowElement(createExclusiveGateway("gateway2", "edit"));
        process.addFlowElement(createEndEvent());

        process.addFlowElement(createSequenceFlow("start", "task1"));
        process.addFlowElement(createSequenceFlow("task1", "gateway1"));
        process.addFlowElement(createSequenceFlowCondition("gateway1", "task2", "${status=='pass'}"));
        process.addFlowElement(createSequenceFlowCondition("gateway1", "task3", "${status=='reject'}"));
        process.addFlowElement(createSequenceFlow("task2", "gateway2"));
        process.addFlowElement(createSequenceFlow("task3", "end"));
        process.addFlowElement(createSequenceFlowCondition("gateway2", "task3", "${status=='edit'}"));
        process.addFlowElement(createSequenceFlowCondition("gateway2", "end", "${status=='ok'}"));

        new BpmnAutoLayout(model).execute();

        // 3. Deploy the process to the engine
        Deployment deployment = repositoryService.createDeployment()
                .addBpmnModel("dynamic-model.bpmn", model).name("Dynamic process deployment").deploy();


        return result;
    }

    protected UserTask createUserTask(String id, String name, String assignee) {
        UserTask userTask = new UserTask();
        userTask.setName(name);
        userTask.setId(id);
        userTask.setAssignee(assignee);
        userTask.setCandidateUsers(Arrays.asList("one", "two", "three"));
        return userTask;
    }

    protected SequenceFlow createSequenceFlow(String from, String to) {
        SequenceFlow flow = new SequenceFlow();
        flow.setSourceRef(from);
        flow.setTargetRef(to);
        return flow;
    }

    protected SequenceFlow createSequenceFlowCondition(String from, String to, String condition) {
        SequenceFlow flow = createSequenceFlow(from, to);
        flow.setConditionExpression(condition);
        return flow;
    }

    protected ExclusiveGateway createExclusiveGateway(String id, String name) {
        ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
        exclusiveGateway.setId(id);
        exclusiveGateway.setName(name);
        return exclusiveGateway;
    }

    protected StartEvent createStartEvent() {
        StartEvent startEvent = new StartEvent();
        startEvent.setId("start");
        return startEvent;
    }

    protected EndEvent createEndEvent() {
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        return endEvent;
    }
}
