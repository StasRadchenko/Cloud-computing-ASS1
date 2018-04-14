package com.company;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.jms.*;

import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import org.apache.commons.codec.binary.Base64;

import static java.lang.Thread.sleep;

public class LocalApplication {
    private static AmazonS3 s3;
    private static AmazonEC2 ec2;
    private static AmazonEC2Client test;
    private static  AmazonSQS sqs;
    private static AWSCredentialsProvider credentialsProvider;
    private static SQSConnectionFactory connectionFactory;
    public static IamInstanceProfileSpecification instanceP;
    private static List<Instance> instances;
    private static String ManagerToLocalQueueID;
    private static String LocalToManagerQueueID;
    private static String ManagerToLocalQueue;
    private static String LocalToManagerQueue;
    private static String bucketName="talstas";
    private static String key;


    public static void main (String [] args){
        File imagesURL= new File(args[0]);
        int numOfImagesPerWorker= Integer.parseInt(args[1]);
        setupProgram();
        uploadFileToS3(imagesURL);
        sendMsgToManager(numOfImagesPerWorker);
       while(!gotResponse()){
           waitSomeTime();
        }
        //downloadResponse();
        close();
    }

    private static void setupProgram( ) {
        instanceP=new IamInstanceProfileSpecification();
        instanceP.setArn("arn:aws:iam::644923746621:instance-profile/ManagerRole");
        credentialsProvider = new AWSStaticCredentialsProvider(
                new EnvironmentVariableCredentialsProvider().getCredentials());
         ec2 = AmazonEC2ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
         connectionFactory = new SQSConnectionFactory(
                new ProviderConfiguration(),
                AmazonSQSClientBuilder.standard()
                        .withRegion("us-east-1")
                        .withCredentials(credentialsProvider));

        if(!isManagerActive())
            defineManager();
    }

    private static boolean isManagerActive() {
        DescribeInstancesResult disResult = ec2.describeInstances();
        List<Reservation> reservationList = disResult.getReservations();
        if (reservationList.size() > 0) {
            for (Reservation res : reservationList) {
                List<Instance> instancesList = res.getInstances();
                for (Instance instance : instancesList) {
                    if (instance.getState().getName().equals("running"))
                        return true;
                }
            }
        }
        return false;
    }

    private static void defineManager() {

        try {
            RunInstancesRequest request = new RunInstancesRequest("ami-76f0061f", 1, 1);
            request.setInstanceType(InstanceType.T1Micro.toString());
            request.setUserData(createManagerScript());
            request.withKeyName("Talbaum1");
            request.setIamInstanceProfile(instanceP);
            instances = ec2.runInstances(request).getReservation().getInstances();
            System.out.println("Launch instances: " + instances);

        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private static String createManagerScript() {
        StringBuilder managerBuild = new StringBuilder();
        managerBuild.append("#!/bin/bash\n"); //start the bash
        //download smthg for using aws commands
        managerBuild.append("curl \"https://s3.amazonaws.com/aws-cli/awscli-bundle.zip\" -o \"awscli-bundle.zip\"\n");
        managerBuild.append("unzip awscli-bundle.zip\n");
        managerBuild.append("sudo ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws \n");
        //download zip from s3
        managerBuild.append("aws s3 cp s3://talstas/manager.zip  manager.zip  \n");
        managerBuild.append("unzip manager.zip\n");
        //need to download java 8 cas the default  at ec2 is java 1.6 and it cant run it -this is whre im stuck
        managerBuild.append("  wget --no-cookies --no-check-certificate --header \"Cookie: gpw_e24=http%3A%2F%2Fwww.oracle.com%2F; oraclelicense=accept-securebackup-cookie\" \"http://download.oracle.com/otn-pub/java/jdk/8u161-b12/2f38c3b165be4555a1fa6e98c45e0808/jdk-8u161-linux-arm32-vfp-hflt.tar.gz\"\n\n");
        managerBuild.append(" rpm -ivh jdk-8u161-linux-arm32-vfp-hflt.tar.gz?AuthParam=1523722363_c1ba95cdc774e8c350e4e12186d00cfc\n  \n");
        //after unziping and downloding java 1.8 run the jar file
        managerBuild.append("java -jar manager.jar\n");

        return new String(Base64.encodeBase64(managerBuild.toString().getBytes()));

    }

    private static void uploadFileToS3(File imagesURLFile) {
         s3 = AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();

        System.out.println("Local App is uploading the input file to S3...");
        key = imagesURLFile.getName().replace('\\', '_').replace('/','_').replace(':', '_');
        PutObjectRequest req = new PutObjectRequest(bucketName, key, imagesURLFile);
        s3.putObject(req);
    }

     private static void sendMsgToManager(int numOfImagesPerWorker) {
        sqs= AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        LocalToManagerQueueID="LocalToManager";
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(LocalToManagerQueueID);
        LocalToManagerQueue = sqs.createQueue(createQueueRequest).getQueueUrl();
        createManagerToLocalQueue();
        sqs.sendMessage(new SendMessageRequest(LocalToManagerQueue,"new task"+"|"+numOfImagesPerWorker +"|" + key));
        // sqs.sendMessage(new SendMessageRequest(LocalToManagerQueue,"new task"));
    }

    private static void createManagerToLocalQueue() {
        ManagerToLocalQueueID="ManagerToLocal";
        CreateQueueRequest createQueueRequest2 = new CreateQueueRequest(ManagerToLocalQueueID);
        ManagerToLocalQueue=sqs.createQueue(createQueueRequest2).getQueueUrl();
        System.out.println("URL OF MANAGER TO LOCAL QUEUE: " +ManagerToLocalQueue);
    }

    private static boolean gotResponse() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(ManagerToLocalQueue);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            System.out.println("not empty");
            if(message.getBody().equals("done task")) {
             System.out.println("GOT RESPONSE!");
                return true;
            }
        }
        System.out.println("NO RESPONSE");
        return false;
    }

    private static void waitSomeTime() {
        try {
            System.out.print("wait loop..");
            sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void downloadResponse() {
        S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
        System.out.println("Downloaded response, Content-Type is: "  + object.getObjectMetadata().getContentType());
        S3ObjectInputStream objectData = object.getObjectContent();
        createOutputFile(objectData);
        try {
            objectData.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createOutputFile(S3ObjectInputStream objectContent) {
    //inputStream or S3ObjectInputStream
        //TODO: build html file containing the pics
    }

    private static void close() {
        closeInstances();
        deleteTheQueues();
        ec2.shutdown();
        s3.shutdown();
        System.out.println("Local Application finished.");
        System.exit(0);
    }

    private static void closeInstances() {
        List<String> toCloseList = new ArrayList<>();
        if(instances!=null) {
            for (Instance i : instances)
                toCloseList.add(i.getInstanceId());
            TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest(toCloseList);
            ec2.terminateInstances(terminateRequest);
        }
    }

    private static void deleteTheQueues() {
        System.out.println("Listing all queues in your account.\n");
        for (String queueUrl : sqs.listQueues().getQueueUrls()) {
            System.out.println("  QueueUrl: " + queueUrl);
        }
        System.out.println();
        gotResponse();

        try {
            SQSConnection connection = connectionFactory.createConnection();
            AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();
            if (client.queueExists("LocalToManager")) {
                System.out.println("LocalToManager queue is alive!");
                client.getAmazonSQSClient().deleteQueue("LocalToManager");
            }
            if (client.queueExists("ManagerToLocal")) {
                System.out.println("Manager to local queue is alive!");
                client.getAmazonSQSClient().deleteQueue("ManagerToLocal");
            }
            connection.close();

        } catch (JMSException e) {
            System.out.println("Queue delete error");
        }

    }

}