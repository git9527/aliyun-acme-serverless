package me.git9527.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GetObjectRequest;
import lombok.extern.slf4j.Slf4j;
import me.git9527.util.EnvKeys;
import me.git9527.util.EnvUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

@Slf4j
public class AliyunStorer {

    public void uploadFile(String localPath) throws FileNotFoundException {
        String bucket = EnvUtil.getEnvValue(EnvKeys.OSS_BUCKET);
        OSS ossClient = this.getOssClient();
        String objectKey = this.getKeyFromPath(localPath);
        InputStream inputStream = new FileInputStream(localPath);
        ossClient.putObject(bucket, objectKey, inputStream);
        ossClient.shutdown();
        logger.info("upload local file:{} to remote:{}", localPath, objectKey);
    }

    public void downloadFile(String localPath) {
        String bucket = EnvUtil.getEnvValue(EnvKeys.OSS_BUCKET);
        OSS ossClient = this.getOssClient();
        String objectKey = this.getKeyFromPath(localPath);
        ossClient.getObject(new GetObjectRequest(bucket, objectKey), new File(localPath));
        ossClient.shutdown();
        logger.info("download remote file:{} to local:{}", objectKey, localPath);
    }

    private String getKeyFromPath(String localPath) {
        String parentFolder = EnvUtil.getEnvValue(EnvKeys.KEYPAIR_FOLDER, "/tmp/acme") + '/';
        return StringUtils.substringAfter(localPath, parentFolder);
    }

    public boolean isFileExist(String localPath) {
        String bucket = EnvUtil.getEnvValue(EnvKeys.OSS_BUCKET);
        String objectKey = this.getKeyFromPath(localPath);
        OSS client = this.getOssClient();
        boolean found = client.doesObjectExist(bucket, objectKey);
        client.shutdown();
        logger.info("check file existence:{}, result:{}", localPath, found);
        return found;
    }

    private OSS getOssClient() {
        String endpoint = EnvUtil.getEnvValue(EnvKeys.OSS_ENDPOINT, "http://oss-cn-shanghai.aliyuncs.com");
        String accessKeyId = EnvUtil.getEnvValue(EnvKeys.OSS_ACCESS_KEY);
        String accessKeySecret = EnvUtil.getEnvValue(EnvKeys.OSS_ACCESS_SECRET);
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
