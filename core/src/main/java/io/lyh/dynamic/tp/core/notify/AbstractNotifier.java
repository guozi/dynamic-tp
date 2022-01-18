package io.lyh.dynamic.tp.core.notify;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.base.Joiner;

import io.lyh.dynamic.tp.common.ApplicationContextHolder;
import io.lyh.dynamic.tp.common.dto.DtpMainProp;
import io.lyh.dynamic.tp.common.dto.Instance;
import io.lyh.dynamic.tp.common.constant.DynamicTpConst;
import io.lyh.dynamic.tp.common.dto.NotifyItem;
import io.lyh.dynamic.tp.common.dto.NotifyPlatform;
import io.lyh.dynamic.tp.common.em.NotifyTypeEnum;
import io.lyh.dynamic.tp.common.em.RejectedTypeEnum;
import io.lyh.dynamic.tp.core.context.DtpContext;
import io.lyh.dynamic.tp.core.context.DtpContextHolder;
import io.lyh.dynamic.tp.core.DtpExecutor;
import io.lyh.dynamic.tp.core.DtpRegistry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.env.Environment;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AbstractNotifier related
 *
 * @author: yanhom
 * @since 1.0.0
 **/
@Slf4j
public abstract class AbstractNotifier implements Notifier {

    private static Instance instance;

    public Instance getInstance() {
        return instance;
    }

    protected AbstractNotifier() {
        init();
    }

    @SneakyThrows
    public static void init() {
        Environment environment = ApplicationContextHolder.getEnvironment();

        String appName = environment.getProperty("spring.application.name");
        appName = StringUtils.isNoneBlank(appName) ? appName : "application";

        String portStr = environment.getProperty("server.port");
        int port = StringUtils.isNotBlank(portStr) ? Integer.parseInt(portStr) : 0;

        String address = InetAddress.getLocalHost().getHostAddress();

        String[] profiles = environment.getActiveProfiles();
        if (profiles.length < 1) {
            profiles = environment.getDefaultProfiles();
        }
        instance = new Instance(address, port, appName, profiles[0]);
    }

    public String buildAlarmContent(NotifyPlatform platform, NotifyTypeEnum typeEnum, String template) {
        DtpContext contextWrapper = DtpContextHolder.get();
        DtpExecutor executor = DtpRegistry.getExecutor(contextWrapper.getDtpExecutor().getThreadPoolName());

        List<String> receivers = StrUtil.split(platform.getReceivers(), ',');
        String receivesStr = Joiner.on(", @").join(receivers);

        NotifyItem notifyItem = contextWrapper.getNotifyItem();
        String content = String.format(
                template,
                getInstance().getServiceName(),
                getInstance().getIp() + ":" + getInstance().getPort(),
                getInstance().getEnv(),
                executor.getThreadPoolName(),
                typeEnum.getValue(),
                notifyItem.getThreshold(),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getLargestPoolSize(),
                executor.getTaskCount(),
                executor.getCompletedTaskCount(),
                executor.getQueue().size(),
                executor.getQueueName(),
                executor.getQueueCapacity(),
                executor.getQueue().size(),
                executor.getQueue().remainingCapacity(),
                RejectedTypeEnum.formatRejectName(executor.getRejectHandlerName()),
                executor.getRejectCount(),
                receivesStr,
                DateUtil.now(),
                notifyItem.getInterval()
        );
        return highlightAlarmContent(content, typeEnum);
    }

    public String buildNoticeContent(NotifyPlatform platform,
                                     String template,
                                     DtpMainProp oldProp,
                                     List<String> diffs) {
        String threadPoolName = oldProp.getDtpName();
        DtpExecutor dtpExecutor = DtpRegistry.getExecutor(threadPoolName);

        List<String> receivers = StrUtil.split(platform.getReceivers(), ',');
        String receivesStr = Joiner.on(", @").join(receivers);

        String content = String.format(
                template,
                getInstance().getServiceName(),
                getInstance().getIp() + ":" + getInstance().getPort(),
                getInstance().getEnv(),
                threadPoolName,
                oldProp.getCorePoolSize(),
                dtpExecutor.getCorePoolSize(),
                oldProp.getMaxPoolSize(),
                dtpExecutor.getMaximumPoolSize(),
                oldProp.isAllowCoreThreadTimeOut(),
                dtpExecutor.allowsCoreThreadTimeOut(),
                oldProp.getKeepAliveTime(),
                dtpExecutor.getKeepAliveTime(TimeUnit.SECONDS),
                dtpExecutor.getQueueName(),
                oldProp.getQueueCapacity(),
                dtpExecutor.getQueueCapacity(),
                RejectedTypeEnum.formatRejectName(oldProp.getRejectType()),
                RejectedTypeEnum.formatRejectName(dtpExecutor.getRejectHandlerName()),
                receivesStr,
                DateTime.now()
        );
        return highlightNotifyContent(content, diffs);
    }

    /**
     * Implement by subclass, get color config.
     * @return left: highlight color, right: other content color
     */
    protected abstract Pair<String, String> getColors();

    private String highlightNotifyContent(String content, List<String> diffs) {
        if (StringUtils.isBlank(content)) {
            return content;
        }

        Pair<String, String> pair = getColors();
        for (String field : diffs) {
            content = content.replace(field, pair.getLeft());
        }
        for (Field field : DtpMainProp.getMainProps()) {
            content = content.replace(field.getName(), pair.getRight());
        }
        return content;
    }

    private String highlightAlarmContent(String content, NotifyTypeEnum typeEnum) {
        if (StringUtils.isBlank(content)) {
            return content;
        }

        List<String> colorKeys = Collections.emptyList();
        if (typeEnum == NotifyTypeEnum.REJECT) {
            colorKeys = DynamicTpConst.REJECT_ALARM_KEYS;
        } else if (typeEnum == NotifyTypeEnum.CAPACITY) {
            colorKeys = DynamicTpConst.CAPACITY_ALARM_KEYS;
        } else if (typeEnum == NotifyTypeEnum.LIVENESS) {
            colorKeys = DynamicTpConst.LIVENESS_ALARM_KEYS;
        }

        Pair<String, String> pair = getColors();
        for (String field : colorKeys) {
            content = content.replace(field, pair.getLeft());
        }
        for (String field : DynamicTpConst.ALL_ALARM_KEYS) {
            content = content.replace(field, pair.getRight());
        }
        return content;
    }
}
