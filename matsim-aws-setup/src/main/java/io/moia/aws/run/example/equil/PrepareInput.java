package io.moia.aws.run.example.equil;

import io.moia.aws.infra.stacks.S3Stack;
import io.moia.aws.run.BatchJobUtils;

import java.nio.file.Path;

public class PrepareInput {

    private final static String bucket = S3Stack.inputBucketName("xxxxxxxxxxxx");
    public static final String SCENARIO = "examples/equil";

    public static void main(String[] args) {


        Path config = Path.of("./scenarios/equil/config.xml");
        BatchJobUtils.uploadFileToS3(bucket, SCENARIO + "/config.xml", config);

        Path network = Path.of("./scenarios/equil/network.xml");
        BatchJobUtils.uploadFileToS3(bucket, SCENARIO + "/network.xml", network);

        Path plans = Path.of("./scenarios/equil/plans100.xml");
        BatchJobUtils.uploadFileToS3(bucket, SCENARIO + "/plans100.xml", plans);

    }
}
