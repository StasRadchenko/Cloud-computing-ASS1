package com.company;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import java.util.List;

import static java.lang.Thread.sleep;

public class Manager {
    private static AWSCredentialsProvider credentialsProvider;
    private static AmazonSQS sqs;
    private static SQSConnectionFactory connectionFactory;
    private static String ManagerToLocalQueue;
    private static String LocalToManagerQueue;
    private static int numOfImagesPerWorker;
    private static String key;
    private static String bucketName= "talstas";

    public static void main (String [] args) {
        System.out.println("WELCOME to manager");
        setup();
        while(!gotTaskFromLocal()){
            try {
                System.out.print("wait loop..");
                sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        String imageList = downloadImageList();
        sendMessageForEachURL(imageList);
        sendMessageToLocalApp();
        startWorkers();
        receiveWorkersData();
        createSummaryFile();
        uploadFiletoS3();

    }

    private static void setup() {
        System.out.println("WELCOME to setup");

        //EC2 PUTTY RUN:
        /* credentialsProvider = new AWSStaticCredentialsProvider
               (new InstanceProfileCredentialsProvider(false).getCredentials());
*/

        //Local run:
        credentialsProvider = new AWSStaticCredentialsProvider(
                new EnvironmentVariableCredentialsProvider().getCredentials());

        System.out.println("credentialsProvider ");
        sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        System.out.println("SQS ");
        connectionFactory = new SQSConnectionFactory(
                new ProviderConfiguration(),
                AmazonSQSClientBuilder.standard()
                        .withRegion("us-east-1")
                        .withCredentials(credentialsProvider));
        System.out.println("connectionFactory ");

        String LocalToManagerID="LocalToManager";
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(LocalToManagerID);
         LocalToManagerQueue=sqs.createQueue(createQueueRequest).getQueueUrl();

        CreateQueueRequest createQueueRequest2 = new CreateQueueRequest("ManagerToLocal");
        ManagerToLocalQueue = sqs.createQueue(createQueueRequest2).getQueueUrl();
        System.out.println("queues ");
    }


    private static boolean gotTaskFromLocal() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(LocalToManagerQueue);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            System.out.println("GOT MSG FROM LOCAL TO MNG");
            parseArgumentsFromLocal(message);
                return true;
        }
        System.out.println("DIDNT GOT MSG FROM LOCAL TO MNG");
        return false;
    }

    private static void parseArgumentsFromLocal(Message msg) {
        String [] args= msg.toString().split("|");
        for(int i=0;i<args.length;i++)
            System.out.println(args[i]);
        numOfImagesPerWorker=Integer.parseInt(args[1].trim());
        key=args[2];
    }

    private static String downloadImageList() {
       /* S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
        System.out.println("Downloaded response, Content-Type is: "  + object.getObjectMetadata().getContentType());
        S3ObjectInputStream objectData = object.getObjectContent();
*/
    return "";}

    private static void sendMessageForEachURL(String imageList) {
    }
    private static void startWorkers() {
    }
    private static void receiveWorkersData() {
    }
    private static void createSummaryFile() {
    }
    private static void uploadFiletoS3() {
    }
    private static void sendMessageToLocalApp() {
        sqs= AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();

        System.out.println("URL OF QUEUE IN MANAGER CLASS: " + ManagerToLocalQueue);
        sqs.sendMessage(new SendMessageRequest(ManagerToLocalQueue,"done task"));
        System.out.println("Messege was sent from manager into the queue");
    }
}
