package com.company;

import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import javax.jms.JMSException;
import java.util.List;

public class Manager {
    private static AWSCredentialsProvider credentialsProvider;
    private static AmazonSQS sqs;
    private static SQSConnectionFactory connectionFactory;
    private static String ManagerToLocalQueue;
    private static String LocalToManagerQueue;

    public static void main (String [] args) {
        setup();
        gotTaskFromLocal(); //should be while
        String imageList = downloadImageList();
        sendMessageForEachURL(imageList);
        sendMessageToLocalApp();
        startWorkers();
        receiveWorkersData();
        createSummaryFile();
        uploadFiletoS3();

    }

    private static void setup() {
        credentialsProvider = new AWSStaticCredentialsProvider(
                new EnvironmentVariableCredentialsProvider().getCredentials());
        sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        connectionFactory = new SQSConnectionFactory(
                new ProviderConfiguration(),
                AmazonSQSClientBuilder.standard()
                        .withRegion("us-east-1")
                        .withCredentials(credentialsProvider));

        String LocalToManagerID="LocalToManager";
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(LocalToManagerID);
         LocalToManagerQueue=sqs.createQueue(createQueueRequest).getQueueUrl();

        CreateQueueRequest createQueueRequest2 = new CreateQueueRequest("ManagerToLocal");
        ManagerToLocalQueue = sqs.createQueue(createQueueRequest2).getQueueUrl();

    }


    private static boolean gotTaskFromLocal() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(LocalToManagerQueue);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            System.out.println("GOT MSG FROM LOCAL TO MNG");
            if(message.getBody().equals("new task"))
                return true;
        }
        System.out.println("DIDNT GOT MSG FROM LOCAL TO MNG");
        return false;
    }

    private static String downloadImageList() {
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
