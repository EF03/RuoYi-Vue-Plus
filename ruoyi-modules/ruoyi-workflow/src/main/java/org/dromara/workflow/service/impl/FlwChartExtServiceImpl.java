package org.dromara.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.dto.UserDTO;
import org.dromara.common.core.service.DeptService;
import org.dromara.common.core.service.UserService;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.PromptContent;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.mapper.FlowHisTaskMapper;
import org.dromara.warm.flow.ui.service.ChartExtService;
import org.dromara.workflow.common.ConditionalOnEnable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程图提示信息
 *
 * @author AprilWind
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwChartExtServiceImpl implements ChartExtService {

    /**
     * 悬浮窗整体样式（支持滚动）
     */
    public static final Map<String, Object> DIALOG_STYLE = Map.ofEntries(
        Map.entry("position", "absolute"),
        Map.entry("backgroundColor", "#fff"),
        Map.entry("border", "1px solid #ccc"),
        Map.entry("borderRadius", "4px"),
        Map.entry("boxShadow", "0 2px 8px rgba(0, 0, 0, 0.15)"),
        Map.entry("padding", "8px 12px"),
        Map.entry("fontSize", "14px"),
        Map.entry("zIndex", 1000),
        Map.entry("maxWidth", "500px"),
        // 取消 maxHeight，让高度自适应
        Map.entry("overflowY", "visible"),
        Map.entry("overflowX", "hidden"),
        Map.entry("color", "#333"),
        Map.entry("pointerEvents", "auto"),
        Map.entry("scrollbarWidth", "thin")
    );
    public static final Map<String, Object> PREFIX_STYLE = Map.of(
        "textAlign", "right",
        "color", "#444",
        "userSelect", "none",
        "display", "inline-block",
        "width", "100px",
        "paddingRight", "8px",
        "fontWeight", "500",
        "fontSize", "14px",
        "lineHeight", "24px",
        "verticalAlign", "middle"
    );
    public static final Map<String, Object> CONTENT_STYLE = Map.of(
        "backgroundColor", "#f7faff",
        "color", "#005cbf",
        "padding", "4px 8px",
        "fontSize", "14px",
        "borderRadius", "4px",
        "whiteSpace", "normal",
        "border", "1px solid #d0e5ff",
        "userSelect", "text",
        "lineHeight", "20px"
    );
    public static final Map<String, Object> ROW_STYLE = Map.of(
        "color", "#222",
        "alignItems", "center",
        "display", "flex",
        "marginBottom", "6px",
        "fontWeight", "400",
        "fontSize", "14px"
    );

    private final UserService userService;
    private final DeptService deptService;
    private final FlowHisTaskMapper flowHisTaskMapper;

    /**
     * 设置流程图提示信息
     *
     * @param defJson 流程定义json对象
     */
    @Override
    public void execute(DefJson defJson) {
        //TODO 等待下一版本更新传递 流程实例id
        Long instanceId = 1935591874325151746L;
        // 按 nodeCode 分组的历史任务列表
        Map<String, List<FlowHisTask>> groupedByNode = this.getHisTaskGroupedByNode(instanceId);

        // 遍历每个节点，处理扩展提示内容
        for (NodeJson nodeJson : defJson.getNodeList()) {
            List<FlowHisTask> taskList = groupedByNode.getOrDefault(nodeJson.getNodeCode(), Collections.emptyList());
            this.processNodeExtInfo(nodeJson, taskList);
        }
    }

    /**
     * 初始化流程图提示信息
     *
     * @param defJson 流程定义json对象
     */
    @Override
    public void initPromptContent(DefJson defJson) {
        ChartExtService.super.initPromptContent(defJson);
        // 为每个节点设置统一的提示框样式
        defJson.getNodeList().forEach(nodeJson -> nodeJson.getPromptContent().setDialogStyle(DIALOG_STYLE));
    }

    /**
     * 处理每个节点的扩展信息，生成提示内容
     *
     * @param nodeJson 当前节点
     */
    private void processNodeExtInfo(NodeJson nodeJson, List<FlowHisTask> taskList) {
        if (CollUtil.isEmpty(taskList)) {
            return;
        }
        List<PromptContent.InfoItem> info = nodeJson.getPromptContent().getInfo();
        for (FlowHisTask task : taskList) {
            UserDTO userDTO = userService.selectUserDtoById(Long.valueOf(task.getApprover()));
            if (ObjectUtil.isEmpty(userDTO)) {
                return;
            }
            String deptName = deptService.selectDeptNameByIds(String.valueOf(userDTO.getDeptId()));
            String displayName = String.format("👤 %s（%s）", userDTO.getNickName(), deptName);

            info.add(new PromptContent.InfoItem()
                .setPrefix(displayName)
                .setPrefixStyle(Map.of(
                    "fontWeight", "bold",
                    "fontSize", "15px",
                    "color", "#333"
                ))
                .setContent("")
                .setContentStyle(Collections.emptyMap())
                .setRowStyle(Map.of("margin", "8px 0", "borderBottom", "1px dashed #ccc"))
            );
            info.add(buildInfoItem("用户账号", userDTO.getUserName()));
            info.add(buildInfoItem("审批耗时", DateUtils.getTimeDifference(task.getUpdateTime(), task.getCreateTime())));
            info.add(buildInfoItem("办理时间", DateUtils.formatDateTime(task.getUpdateTime())));
        }
    }

    /**
     * 构建单条提示内容对象 InfoItem，用于悬浮窗显示（key: value）
     *
     * @param key   字段名（作为前缀）
     * @param value 字段值
     * @return 提示项对象
     */
    private PromptContent.InfoItem buildInfoItem(String key, String value) {
        return new PromptContent.InfoItem()
            .setPrefix(key + ": ")
            .setPrefixStyle(PREFIX_STYLE)
            .setContent(value)
            .setContentStyle(CONTENT_STYLE)
            .setRowStyle(ROW_STYLE);
    }

    /**
     * 根据流程实例ID获取按节点编号分组的历史任务列表
     *
     * @param instanceId 流程实例ID
     * @return Map<节点编码, 对应的历史任务列表>
     */
    public Map<String, List<FlowHisTask>> getHisTaskGroupedByNode(Long instanceId) {
        LambdaQueryWrapper<FlowHisTask> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FlowHisTask::getInstanceId, instanceId);
        wrapper.eq(FlowHisTask::getNodeType, NodeType.BETWEEN.getKey());
        wrapper.orderByDesc(FlowHisTask::getCreateTime).orderByDesc(FlowHisTask::getUpdateTime);
        List<FlowHisTask> flowHisTasks = flowHisTaskMapper.selectList(wrapper);

        return flowHisTasks.stream()
            .collect(Collectors.groupingBy(FlowHisTask::getNodeCode));
    }

}
