package com.jerryl.activiti.controller;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jerryl.common.RestServiceController;
import com.jerryl.util.Status;
import com.jerryl.util.ToWeb;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.IdentityService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.expression.Maps;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

/**
 * Created by liuruijie on 2017/4/20.
 * 模型管理
 */
@RestController
@RequestMapping("/models")
public class ModelerController implements RestServiceController<Model, String>{

    @Autowired
    RepositoryService repositoryService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    IdentityService identityService;

    /**
     * 新建一个空模型
     * @return
     * @throws UnsupportedEncodingException
     */
    @PostMapping("/newModel")
    public Object newModel() throws UnsupportedEncodingException {
        //初始化一个空模型
        Model model = repositoryService.newModel();

        //设置一些默认信息
        String name = "new-process";
        String description = "";
        int revision = 1;
        String key = "process";

        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(ModelDataJsonConstants.MODEL_NAME, name);
        modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, description);
        modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);

        model.setName(name);
        model.setKey(key);
        model.setMetaInfo(modelNode.toString());

        repositoryService.saveModel(model);
        String id = model.getId();

        //完善ModelEditorSource
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");
        ObjectNode stencilSetNode = objectMapper.createObjectNode();
        stencilSetNode.put("namespace",
                "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.put("stencilset", stencilSetNode);
        repositoryService.addModelEditorSource(id,editorNode.toString().getBytes("utf-8"));
        return ToWeb.buildResult().redirectUrl("/editor?modelId="+id);
    }


    /**
     * 发布模型为流程定义
     * @param id
     * @return
     * @throws Exception
     */
    @PostMapping("/{id}/deployment")
    public Object deploy(@PathVariable("id")String id) throws Exception {

        //获取模型
        Model modelData = repositoryService.getModel(id);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

        if (bytes == null) {
            return ToWeb.buildResult().status(Status.FAIL)
                    .msg("模型数据为空，请先设计流程并成功保存，再进行发布。");
        }

        JsonNode modelNode = new ObjectMapper().readTree(bytes);

        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if(model.getProcesses().size()==0){
            return ToWeb.buildResult().status(Status.FAIL)
                    .msg("数据模型不符要求，请至少设计一条主线流程。");
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        byte[] modelEditorSourceExtra = repositoryService.getModelEditorSourceExtra(modelData.getId());
        System.out.println(modelEditorSourceExtra.length);

        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        String processImage = modelData.getName() + ".png";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addInputStream(processName, new ByteArrayInputStream(bpmnBytes))
                .addInputStream(processImage, new ByteArrayInputStream(modelEditorSourceExtra))
                .deploy();

        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);

        return ToWeb.buildResult().refresh();
    }

    @GetMapping("/{id}/image")
    @ResponseBody
    public void image(@PathVariable("id")String id, HttpServletResponse response) throws Exception {
        System.out.println("id: " + id);

        Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(id).singleResult();

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(id).singleResult();
        System.out.println(processDefinition.getDiagramResourceName());

        InputStream png = repositoryService.getResourceAsStream(deployment.getId(), processDefinition.getDiagramResourceName());
        byte[] b = new byte[1024];
        int len = -1;
        while((len = png.read(b, 0, 1024)) != -1) {
            response.getOutputStream().write(b, 0, len);
        }
    }

    @GetMapping("/{id}/user")
    @ResponseBody
    public Map<String, Object> users(@PathVariable("id")String id, String name, String email, String passwd) {
        Map<String, Object> result = new HashMap<>(1);

        User user = identityService.newUser(name);
        user.setEmail(email);
        user.setPassword(passwd);

        identityService.saveUser(user);

        User user1 = identityService.createUserQuery().userId(name).singleResult();
        System.out.println(JSON.toJSONString(user1));

        result.put("code", 200);

        return result;
    }

    @GetMapping("/{id}/group")
    @ResponseBody
    public Map<String, Object> group(@PathVariable("id")String id, String groupid, String name, String type) {
        Map<String, Object> result = new HashMap<>(1);
        System.out.println(name);

        Group group = identityService.newGroup(groupid);
        group.setName(name);
        group.setType(type);
        identityService.saveGroup(group);

        identityService.createMembership("jack", groupid);

        result.put("code", 200);

        return result;
    }

    @Override
    public Object getOne(@PathVariable("id") String id) {
        Model model = repositoryService.createModelQuery().modelId(id).singleResult();
        return ToWeb.buildResult().setObjData(model);
    }

    @Override
    public Object getList(@RequestParam(value = "rowSize", defaultValue = "1000", required = false) Integer rowSize,
                          @RequestParam(value = "page", defaultValue = "1", required = false) Integer page) {
        List<Model> list = repositoryService.createModelQuery().listPage(rowSize * (page - 1)
                , rowSize);
        long count = repositoryService.createModelQuery().count();

        return ToWeb.buildResult().setRows(
                ToWeb.Rows.buildRows().setCurrent(page)
                        .setTotalPages((int) (count/rowSize+1))
                        .setTotalRows(count)
                        .setList(list)
                        .setRowSize(rowSize)
        );
    }

    @Override
    public Object deleteOne(@PathVariable("id")String id){
        repositoryService.deleteModel(id);
        return ToWeb.buildResult().refresh();
    }

    @Override
    public Object postOne(@RequestBody Model entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object putOne(@PathVariable("id") String s, @RequestBody Model entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object patchOne(@PathVariable("id") String s, @RequestBody Model entity) {
        throw new UnsupportedOperationException();
    }


}
