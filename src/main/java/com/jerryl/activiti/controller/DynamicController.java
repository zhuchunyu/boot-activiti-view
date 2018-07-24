package com.jerryl.activiti.controller;

import com.google.common.collect.Maps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jerryl.activiti.config.IdGen;
import com.jerryl.activiti.listener.TestTaskListener;
import com.jerryl.activiti.model.ConditiionRoute;
import com.jerryl.activiti.model.ProcessNode;

import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.model.ActivitiListener;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.EndEvent;
import org.activiti.bpmn.model.ExclusiveGateway;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.Gateway;
import org.activiti.bpmn.model.ImplementationType;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.ServiceTask;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.IdentityService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.batik.gvt.flow.FlowTextLayoutFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.fo.pagination.Flow;
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

    static Map<Integer, String> types = Maps.newHashMap();
    static {
        types.put(1, "开始");
        types.put(2, "结束");
        types.put(3, "角色网关");
        types.put(4, "审核");
        types.put(5, "服务");
    }

    Map<String, Long> newMap(String key, Long value) {
        Map<String, Long> map = Maps.newHashMap();
        map.put(key, value);
        return map;
    }

    @GetMapping("/generate")
    public Map<String, Object> generate() {
        Map<String, Object> result = Maps.newHashMap();

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
        gateway.setConditions(Arrays.asList(new ConditiionRoute("101", 5L),
                new ConditiionRoute("201", 4L), new ConditiionRoute("203", 3L)));
        nodes.add(gateway);

        ProcessNode projectNode = new ProcessNode();
        projectNode.setId(3L);
        projectNode.setName("项目管理员审核");
        projectNode.setType(4);
        projectNode.setRoles(Arrays.asList("projet-203", "user-1001"));
        projectNode.setConditionType("whether");
        projectNode.setConditions(Arrays.asList(new ConditiionRoute("pass", 4L), new ConditiionRoute("reject", 6L)));
        nodes.add(projectNode);

        ProcessNode companyNode = new ProcessNode();
        companyNode.setId(4L);
        companyNode.setName("企业管理员审核");
        companyNode.setType(4);
        companyNode.setRoles(Arrays.asList("company-101", "user-1203"));
        companyNode.setConditionType("whether");
        companyNode.setConditions(Arrays.asList(new ConditiionRoute("pass", 5L), new ConditiionRoute("reject", 6L)));
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
        cancelNode.setName("拒绝并关闭");
        cancelNode.setType(5);
        cancelNode.setServices(Arrays.asList(3L));
        cancelNode.setNextNode(7L);
        nodes.add(cancelNode);

        ProcessNode nodeEnd = new ProcessNode();
        nodeEnd.setId(7L);
        nodeEnd.setName("结束");
        nodeEnd.setType(2);
        nodes.add(nodeEnd);

        Map<Long, FlowElement> flows = Maps.newHashMap();
        Map<Long, FlowElement> flowsAdditionals = Maps.newHashMap();

        BpmnModel model = new BpmnModel();
        Process process = new Process();
        model.addProcess(process);
        process.setId("serviceApply");

        IdGen idGen = new IdGen();

        for (ProcessNode node : nodes) {
            if (node.getType().equals(1)) {
                FlowElement startFlow = startEvent(longToHex(idGen.nextId()), node);
                process.addFlowElement(startFlow);
                flows.put(node.getId(), startFlow);
            } else if (node.getType().equals(3)) {
                FlowElement roleFlow = gatewayEvent(longToHex(idGen.nextId()), node);
                process.addFlowElement(roleFlow);
                flows.put(node.getId(), roleFlow);
            } else if (node.getType().equals(4)) {
                FlowElement taskFlow = taskEvent(longToHex(idGen.nextId()), node);
                process.addFlowElement(taskFlow);
                flows.put(node.getId(), taskFlow);

                FlowElement whetherFlow = gatewayEvent(longToHex(idGen.nextId()), node);
                process.addFlowElement(whetherFlow);
                flowsAdditionals.put(node.getId(), whetherFlow);
            } else if (node.getType().equals(5)) {
                FlowElement openFlow = serviceEvent(longToHex(idGen.nextId()), node);
                process.addFlowElement(openFlow);
                flows.put(node.getId(), openFlow);
            } else if (node.getType().equals(2)) {
                FlowElement endFlow = endEvent(longToHex(idGen.nextId()), node);
                process.addFlowElement(endFlow);
                flows.put(node.getId(), endFlow);
            }
        }

        for (ProcessNode node : nodes) {
            if (node.getType().equals(1)) {
                FlowElement startFlow = flows.get(node.getId());
                FlowElement nextFlow = flows.get(node.getNextNode());
                process.addFlowElement(sequenceFlow(startFlow.getId(), nextFlow.getId()));
            } else if (node.getType().equals(3)) {
                FlowElement roleFlow = flows.get(node.getId());
                String conditionType = node.getConditionType();
                List<ConditiionRoute> conditions = node.getConditions();
                for (ConditiionRoute route:conditions) {
                    FlowElement nextFlow = flows.get(route.getFlowId());
                    process.addFlowElement(sequenceFlow(roleFlow.getId(), nextFlow.getId(),
                            route.getValue(), String.format("${%s=='%s'}", conditionType, route.getValue())));
                }
            } else if (node.getType().equals(4)) {
                FlowElement userFlow = flows.get(node.getId());
                FlowElement wheatherFlow = flowsAdditionals.get(node.getId());
                String conditionType = node.getConditionType();
                List<ConditiionRoute> conditions = node.getConditions();

                process.addFlowElement(sequenceFlow(userFlow.getId(), wheatherFlow.getId()));

                for (ConditiionRoute route:conditions) {
                    FlowElement nextFlow = flows.get(route.getFlowId());
                    process.addFlowElement(sequenceFlow(wheatherFlow.getId(), nextFlow.getId(),
                            route.getValue(), String.format("${%s=='%s'}", conditionType, route.getValue())));
                }
            } else if (node.getType().equals(5)) {
                FlowElement serviceFlow = flows.get(node.getId());
                FlowElement nextFlow = flows.get(node.getNextNode());
                process.addFlowElement(sequenceFlow(serviceFlow.getId(), nextFlow.getId()));
            }
        }

        new BpmnAutoLayout(model).execute();

        Deployment deployment = repositoryService.createDeployment()
                .addBpmnModel("serviceApply.bpmn", model).name("Service Apply Dynamic process deployment").deploy();

        System.out.println("deployment: " + deployment.getId());
        repositoryService.deleteDeployment(deployment.getId(), true);

        result.put("types", types);
        result.put("nodes", nodes);
        result.put("status", 200);
        return result;
    }

    String longToHex(long id) {
        return "id-" + Long.toHexString(id);
    }

    SequenceFlow sequenceFlow(String from, String to, String name, String condition) {
        SequenceFlow flow = sequenceFlow(from, to, name);
        flow.setConditionExpression(condition);

        return flow;
    }

    SequenceFlow sequenceFlow(String from, String to, String name) {
        SequenceFlow flow = sequenceFlow(from, to);
        flow.setName(name);

        return flow;
    }

    SequenceFlow sequenceFlow(String from, String to) {
        SequenceFlow flow = new SequenceFlow();

        flow.setSourceRef(from);
        flow.setTargetRef(to);

        return flow;
    }

    EndEvent endEvent(String id, ProcessNode processNode) {
        EndEvent endEvent = new EndEvent();
        endEvent.setId(id);
        endEvent.setName(processNode.getName());

        return endEvent;
    }

    ServiceTask serviceEvent(String id, ProcessNode processNode) {
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId(id);
        serviceTask.setName(processNode.getName());
        serviceTask.setImplementationType("expression");
        serviceTask.setImplementation("#{serviceOrderService.openService(execution)}");

        return serviceTask;
    }

    UserTask taskEvent(String id, ProcessNode processNode) {
        UserTask userTask = new UserTask();
        userTask.setId(id);
        userTask.setName(processNode.getName());
        userTask.setCandidateUsers(processNode.getRoles());

        return userTask;
    }

    ExclusiveGateway gatewayEvent(String id, ProcessNode processNode) {
        ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
        exclusiveGateway.setId(id);
        exclusiveGateway.setName(processNode.getName());

        return exclusiveGateway;
    }

    StartEvent startEvent(String id, ProcessNode processNode) {
        StartEvent startEvent = new StartEvent();
        startEvent.setId(id);
        startEvent.setName(processNode.getName());

        return startEvent;
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
