package com.oj.ojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;

/**
 * @ClassName: DockerDemo
 * @author: mafangnian
 * @date: 2023/10/17 17:22
 */
public class DockerDemo {
    public static void main(String[] args) throws InterruptedException {

        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "nginx:latest";
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
            @Override
            public void onNext(PullResponseItem item) {
                System.out.println("下载镜像:"+ item.getStatus());
                super.onNext(item);
            }
        };
        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
        System.out.println("下载完成");
    }
}
