package com.company;
import java.io.File;
import java.util.List;
import java.util.UUID;

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
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class LocalApplication {
    private static AmazonS3 s3;
    private static AmazonEC2 ec2;
    private static  AmazonSQS sqs;
    private static AWSCredentialsProvider credentialsProvider;
    private static String bucketName ="talstas";
    private static String key;

    public static void main (String [] args){
        File imagesURL= new File(args[0]);
        int numOfImagesPerWorker= Integer.parseInt(args[1]);
        setupManager();
        uploadFileToS3(imagesURL);
        sendMsgToManager(); // need to send numOfImagesPerWorker
        CreateQueueRequest createQueueRequest2 = new CreateQueueRequest("ManagerToLocal"+ UUID.randomUUID());
        String ManagerToLocalQueue = sqs.createQueue(createQueueRequest2).getQueueUrl();
        while(!checkForResponse(ManagerToLocalQueue)){
            //wait a bit?
        }
        downloadResponse();
        close();
    }

    private static void setupManager( ) {
        credentialsProvider = new AWSStaticCredentialsProvider(
                new EnvironmentVariableCredentialsProvider().getCredentials());
         ec2 = AmazonEC2ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
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
            List<Instance> instances = ec2.runInstances(request).getReservation().getInstances();
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

    private static void sendMsgToManager() {
        sqs= AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        CreateQueueRequest createQueueRequest = new CreateQueueRequest("LocalToManager"+ UUID.randomUUID());
        String LocalToManagerQueue = sqs.createQueue(createQueueRequest).getQueueUrl();
        sqs.sendMessage(new SendMessageRequest(LocalToManagerQueue,"new task"));
    }

    private static boolean checkForResponse(String ManagerToLocalQueue) {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(ManagerToLocalQueue);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            if(message.getBody().equals("done task"))
                return true;
        }
        return false;
    }

    private static void downloadResponse() {
        S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
        System.out.println("Content-Type: "  + object.getObjectMetadata().getContentType());
        createOutput(object.getObjectContent());

    }

    private static void createOutput(S3ObjectInputStream objectContent) {
    }

    private static void close() {
    }

}
