package com.xiaozhi.dialogue.stt.factory;

import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.dialogue.stt.providers.*;
import com.xiaozhi.entity.SysConfig;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SttServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(SttServiceFactory.class);

    // 缓存已初始化的服务：key format: "provider:configId"
    private final Map<String, SttService> serviceCache = new ConcurrentHashMap<>();

    // 默认服务提供商名称 - 改为FunASR以避免内存问题
    private static final String DEFAULT_PROVIDER = "funasr";

    // 默认FunASR服务URL (可通过环境变量覆盖)
    private static final String DEFAULT_FUNASR_URL = System.getenv().getOrDefault("FUNASR_API_URL", "ws://localhost:10095/");

    // 标记默认服务是否初始化成功
    private boolean defaultServiceInitialized = false;

    // 备选默认提供商（当默认初始化失败时使用）
    private String fallbackProvider = null;

    /**
     * 应用启动时自动初始化默认语音识别服务
     */
    @PostConstruct
    public void initializeDefaultSttService() {
        logger.info("正在初始化默认语音识别服务(FunASR)...");
        try {
            // 创建默认FunASR配置
            SysConfig defaultConfig = new SysConfig()
                .setProvider(DEFAULT_PROVIDER)
                .setApiUrl(DEFAULT_FUNASR_URL)
                .setConfigId(-1);
            
            // 初始化FunASR服务
            SttService defaultService = createApiService(defaultConfig);
            serviceCache.put(DEFAULT_PROVIDER, defaultService);
            defaultServiceInitialized = true;
            
            logger.info("默认语音识别服务(FunASR)初始化成功，服务地址: {}", DEFAULT_FUNASR_URL);
        } catch (Exception e) {
            logger.warn("默认语音识别服务(FunASR)初始化失败: {}，将在配置后使用", e.getMessage());
            defaultServiceInitialized = false;
        }
    }

    /**
     * 获取默认STT服务
     */
    public SttService getDefaultSttService() {
        return getSttService(null);
    }

    /**
     * 根据配置获取STT服务
     */
    public SttService getSttService(SysConfig config) {
        if (config == null) {
            config = new SysConfig()
                .setProvider(DEFAULT_PROVIDER)
                .setApiUrl(DEFAULT_FUNASR_URL)
                .setConfigId(-1);
        }

        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        var cacheKey = config.getProvider() + ":" + config.getConfigId();

        // 检查是否已有该配置的服务实例
        if (serviceCache.containsKey(cacheKey)) {
            return serviceCache.get(cacheKey);
        }

        // 创建新的API服务实例
        var service = createApiService(config);
        serviceCache.put(cacheKey, service);

        // 如果没有备选默认服务，将此服务设为备选
        if (fallbackProvider == null) {
            fallbackProvider = cacheKey;
        }

        return service;
    }

    /**
     * 根据配置创建API类型的STT服务
     */
    private SttService createApiService(@Nonnull SysConfig config) {
        return switch (config.getProvider()) {
            case "tencent" -> new TencentSttService(config);
            case "aliyun" -> new AliyunSttService(config);
            case "funasr" -> new FunASRSttService(config);
            case "xfyun" -> new XfyunSttService(config);
            case "vosk" -> {
                // 仅在明确指定时才尝试初始化Vosk
                logger.warn("Vosk模型已禁用以节省内存，建议使用FunASR或其他云服务");
                if (fallbackProvider != null && serviceCache.containsKey(fallbackProvider)) {
                    yield serviceCache.get(fallbackProvider);
                }
                throw new RuntimeException("Vosk service is disabled for memory optimization. Please use FunASR or other cloud services.");
            }
            default -> {
                // 默认使用FunASR
                yield new FunASRSttService(config);
            }
        };
    }

    public void removeCache(SysConfig config) {
        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        Integer configId = config.getConfigId();
        String provider = config.getProvider();
        String cacheKey = provider + ":" + (configId != null ? configId : "default");
        serviceCache.remove(cacheKey);
    }
}