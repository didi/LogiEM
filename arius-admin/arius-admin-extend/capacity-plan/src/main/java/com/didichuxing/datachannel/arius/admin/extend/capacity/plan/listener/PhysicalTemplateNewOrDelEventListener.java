package com.didichuxing.datachannel.arius.admin.extend.capacity.plan.listener;

import com.didichuxing.datachannel.arius.admin.common.bean.po.template.TemplateLogicPO;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplateLogicDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogicWithPhyTemplates;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.event.template.PhysicalTemplateAddEvent;
import com.didichuxing.datachannel.arius.admin.common.event.template.PhysicalTemplateDeleteEvent;
import com.didichuxing.datachannel.arius.admin.common.event.template.PhysicalTemplateEvent;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.entity.CapacityPlanRegion;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.CapacityPlanRegionService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;

/**
 * @author d06679
 * @date 2019-08-04
 */
@Component
public class PhysicalTemplateNewOrDelEventListener implements ApplicationListener<PhysicalTemplateEvent> {

    private static final ILog         LOGGER = LogFactory.getLog(PhysicalTemplateNewOrDelEventListener.class);

    @Autowired
    private CapacityPlanRegionService capacityPlanRegionService;

    @Autowired
    private IndexTemplateLogicDAO     indexTemplateLogicDAO;

    /**
     * Handle an application event.
     *
     * @param event the event to respond to
     */
    @Override
    public void onApplicationEvent(PhysicalTemplateEvent event) {
        Double deltaQuota = getDeltaQuota(event);
        IndexTemplatePhy templatePhysical = getOpTemplate(event);
        if (deltaQuota != 0.0 && templatePhysical != null) {
            LOGGER.info("class=PhysicalTemplateNewOrDelEventListener||method=onApplicationEvent||logicId={}||physicalCluster={}||templateName={}||deltaQuota={}",
                    templatePhysical.getLogicId(), templatePhysical.getCluster(), templatePhysical.getName(), deltaQuota);
            updateRegionQuota(templatePhysical, deltaQuota);
            updateLogicTemplateQuota(templatePhysical.getLogicId(), deltaQuota);
        }

    }

    /***************************************** private method ****************************************************/
    /**
     * ??????????????????Quota
     * @param logicId ????????????ID
     * @param deltaQuota Quota??????
     */
    private void updateLogicTemplateQuota(Integer logicId, Double deltaQuota) {
        TemplateLogicPO logicPO = indexTemplateLogicDAO.getById(logicId);
        if (logicPO != null) {
            logicPO.setQuota(logicPO.getQuota() + deltaQuota);
            if (indexTemplateLogicDAO.update(logicPO) == 0) {
                LOGGER.error("class=PhysicalTemplateNewOrDelEventListener||method=updateLogicTemplateQuota||errMsg=updateTemplateQuotaFail||logicId={}||targetQuota={}||deltaQuota={}",
                        logicPO.getId(), logicPO.getQuota(), deltaQuota);
            }
        }
    }

    /**
     * ??????Region  Quota??????
     * @param templatePhysical ????????????
     * @param deltaQuota ??????Quota
     */
    private void updateRegionQuota(IndexTemplatePhy templatePhysical, Double deltaQuota) {
        CapacityPlanRegion region = capacityPlanRegionService.getRegionOfPhyTemplate(templatePhysical);
        if (region != null) {
            LOGGER.info("class=PhysicalTemplateNewOrDelEventListener||method=onApplicationEvent||region={}||deltaQuota={}||msg=PhysicalTemplateAddEvent", region,
                    deltaQuota);
            capacityPlanRegionService.editRegionFreeQuota(region.getRegionId(), region.getFreeQuota() - deltaQuota);
        }
    }

    private IndexTemplatePhy getOpTemplate(PhysicalTemplateEvent event) {
        if (event instanceof PhysicalTemplateAddEvent) {
            return ((PhysicalTemplateAddEvent) event).getNewTemplate();
        } else if (event instanceof PhysicalTemplateDeleteEvent) {
            return ((PhysicalTemplateDeleteEvent) event).getDelTemplate();
        }

        return null;
    }

    /**
     * ?????????????????????????????????????????????????????????????????????Quota
     * ?????????????????????????????????????????????Quota????????????????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????
     * ????????????????????????Quota????????????????????????????????????????????????????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????????????????????????????Quota?????????????????????
     * @param event ????????????
     * @return
     */
    private Double getDeltaQuota(PhysicalTemplateEvent event) {
        IndexTemplateLogicWithPhyTemplates logicWithPhysical = event.getLogicWithPhysical();
        if (logicWithPhysical.hasPhysicals()) {
            long currentPhysicalSize = logicWithPhysical.getPhysicals().size();

            if (event instanceof PhysicalTemplateAddEvent) {
                if (currentPhysicalSize > 1) {
                    currentPhysicalSize = currentPhysicalSize - 1;
                    return logicWithPhysical.getQuota() / currentPhysicalSize;
                }
            } else if (event instanceof PhysicalTemplateDeleteEvent) {
                return -1 * logicWithPhysical.getQuota() / ( currentPhysicalSize + 1);
            }
        }

        return 0.0;
    }
}
