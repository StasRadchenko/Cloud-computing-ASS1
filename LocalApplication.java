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
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import static java.lang.Thread.sleep;

public class LocalApplication {
    private static AmazonS3 s3;
    private static AmazonEC2 ec2;
    private static  AmazonSQS sqs;
    private static AWSCredentialsProvider credentialsProvider;
    private static SQSConnectionFactory connectionFactory;
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
        //while(!gotResponse(ManagerToLocalQueue)){
       //     waitSomeTime();
      //  }
       // downloadResponse();
        close();
    }

    private static void setupProgram( ) {
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
            instances = ec2.runInstances(request).getReservation().getInstances();
            System.out.println("Launch instances: " + instances);

        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
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
        LocalToManagerQueueID="LocalToManager"+ UUID.randomUUID();
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(LocalToManagerQueueID);
        LocalToManagerQueue = sqs.createQueue(createQueueRequest).getQueueUrl();
        sqs.sendMessage(new SendMessageRequest(LocalToManagerQueue,"new task"));
        createManagerToLocalQueue();
    }

    private static void createManagerToLocalQueue() {
        ManagerToLocalQueueID="ManagerToLocal"+ UUID.randomUUID();
        CreateQueueRequest createQueueRequest2 = new CreateQueueRequest(ManagerToLocalQueueID);
        ManagerToLocalQueue = sqs.createQueue(createQueueRequest2).getQueueUrl();
    }

    private static boolean gotResponse(String ManagerToLocalQueue) {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(ManagerToLocalQueue);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            if(message.getBody().equals("done task"))
                return true;
        }
        return false;
    }

    private static void waitSomeTime() {
        try {
            System.out.print("wait loop..");
            sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void downloadResponse() {
        S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
        System.out.println("Downloaded response, Content-Type is: "  + object.getObjectMetadata().getContentType());
        InputStream objectData = object.getObjectContent();
        createOutputFile(objectData);
        try {
            objectData.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createOutputFile(InputStream objectContent) {
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
        try {
            SQSConnection connection = connectionFactory.createConnection();
            AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();
            client.getAmazonSQSClient().deleteQueue(LocalToManagerQueueID);
            client.getAmazonSQSClient().deleteQueue(ManagerToLocalQueueID);
            connection.close();

        } catch (JMSException e) {
            System.out.println("Queue delete error");
        }
    }

}