package com.didichuxing.datachannel.arius.admin.task.template;

import com.didichuxing.datachannel.arius.admin.biz.template.srv.expire.TemplateExpireManager;
import com.didichuxing.datachannel.arius.admin.task.TaskConcurrentConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.task.BaseConcurrentClusterTask;

/**
 * 删除过期索引任务
 * @author d06679
 * @date 2019/4/8
 */
@Component
public class DeleteExpireIndexTask extends BaseConcurrentClusterTask {

    @Autowired
    private TemplateExpireManager templateExpireManager;

    /**
     * 获取任务名称
     *
     * @return 任务名称
     */
    @Override
    public String getTaskName() {
        return "删除过期索引";
    }

    /**
     * 任务的线程个数
     * @return 任务的线程个数
     */
    @Override
    public int poolSize() {
        return 20;
    }

    /**
     * 并发度
     *
     * @return
     */
    @Override
    public int current() {
        return TaskConcurrentConstants.DELETE_EXPIRE_INDEX_TASK_CONCURRENT;
    }

    /**
     * 同步处理指定集群
     *
     * @param cluster 集群名字
     */
    @Override
    public boolean executeByCluster(String cluster) throws AdminOperateException {
        return templateExpireManager.deleteExpireIndex(cluster);
    }
}
