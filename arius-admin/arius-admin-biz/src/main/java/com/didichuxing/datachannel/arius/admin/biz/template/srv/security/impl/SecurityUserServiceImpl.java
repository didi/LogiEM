package com.didichuxing.datachannel.arius.admin.biz.template.srv.security.impl;

import com.didichuxing.datachannel.arius.admin.biz.template.srv.security.SecurityUserService;
import com.didiglobal.logi.elasticsearch.client.request.security.SecurityUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.persistence.component.ESOpTimeoutRetry;
import com.didichuxing.datachannel.arius.admin.persistence.es.cluster.ESSecurityUserDAO;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;

/**
 * @author didi
 */
@Service
public class SecurityUserServiceImpl implements SecurityUserService {

    private static final ILog LOGGER = LogFactory.getLog(SecurityUserServiceImpl.class);

    @Autowired
    private ESSecurityUserDAO esSecurityUserDAO;

    @Autowired
    private AppService        appService;

    /**
     * 为用户添加角色
     *
     *
     * @param cluster    集群
     * @param userName   用户
     * @param roleName   角色名字
     * @param appId      appid
     * @param retryCount 重试次数
     * @return result
     */
    @Override
    public Result<Boolean> appendUserRoles(String cluster, String userName, String roleName, Integer appId,
                                  int retryCount) throws ESOperateException {
        SecurityUser securityUser = esSecurityUserDAO.getByName(cluster, userName);

        if (securityUser == null) {
            securityUser = new SecurityUser();
            securityUser.setRoles(Lists.newArrayList());
        }

        securityUser.setPassword(appService.getAppById(appId).getVerifyCode());
        securityUser.getRoles().add(roleName);

        LOGGER.info("class=SecurityUserServiceImpl||method=appendUserRoles||cluster={}||userName={}||roleName={}", cluster, userName, roleName);

        SecurityUser finalSecurityUser = securityUser;
        return Result.buildBoolen(ESOpTimeoutRetry.esRetryExecute("appendUserRoles", retryCount,
            () -> esSecurityUserDAO.putUser(cluster, userName, finalSecurityUser)));
    }

    /**
     * 删除用户角色
     *
     *
     * @param cluster    集群
     * @param userName   用户
     * @param roleName   角色名字
     * @param retryCount 重试次数
     * @return result
     */
    @Override
    public Result<Boolean> deleteUserRoles(String cluster, String userName, String roleName,
                                  int retryCount) throws ESOperateException {
        SecurityUser securityUser = esSecurityUserDAO.getByName(cluster, userName);

        if (securityUser == null) {
            return Result.buildSucc();
        }

        securityUser.getRoles().remove(roleName);

        LOGGER.info("class=SecurityUserServiceImpl||method=deleteUserRoles||cluster={}||userName={}||roleName={}", cluster, userName, roleName);

        return Result.buildBoolen(ESOpTimeoutRetry.esRetryExecute("deleteUserRoles", retryCount,
            () -> esSecurityUserDAO.putUser(cluster, userName, securityUser)));
    }

    /**
     * 确保用户拥有制定权限
     *
     * @param cluster  集群
     * @param userName 用户
     * @param roleName 权限
     * @param appId appid
     * @return result
     */
    @Override
    public Result<Boolean> ensureUserHasAuth(String cluster, String userName, String roleName, Integer appId) {
        try {
            SecurityUser securityUser = esSecurityUserDAO.getByName(cluster, userName);
            if (securityUser == null || !securityUser.getRoles().contains(roleName)) {
                Result<Boolean> result = appendUserRoles(cluster, userName, roleName, appId, 3);
                LOGGER.info("class=SecurityUserServiceImpl||method=ensureUserHasAuth||cluster={}||userName={}||roleName={}||result={}",
                        cluster, userName, roleName, result.getMessage());
                return result;
            } else {
                return Result.buildSucc(true);
            }

        } catch (Exception e) {
            LOGGER.error("class=SecurityUserServiceImpl||method=ensureUserHasAuth||cluster={}||userName={}||roleName={}||errMsg={}",
                    cluster, userName, roleName, e.getMessage(), e);
            return Result.buildFail(e.getMessage());
        }

    }

    /**
     * 修改密码
     *
     * @param cluster    集群
     * @param userName   用户名
     * @param verifyCode 密码
     * @param retryCount retryCount
     * @return
     */
    @Override
    public Result<Boolean> changePasswordIfExist(String cluster, String userName, String verifyCode,
                                        int retryCount) throws ESOperateException {
        SecurityUser securityUser = esSecurityUserDAO.getByName(cluster, userName);

        if (securityUser == null) {
            return Result.buildSucc();
        }

        securityUser.setPassword(verifyCode);

        LOGGER.info("class=SecurityUserServiceImpl||method=deleteUserRoles||cluster={}||userName={}||verifyCode={}",
                cluster, userName, verifyCode);

        return Result.buildBoolen(ESOpTimeoutRetry.esRetryExecute("changePasswordIfExist", retryCount,
            () -> esSecurityUserDAO.putUser(cluster, userName, securityUser)));
    }
}
