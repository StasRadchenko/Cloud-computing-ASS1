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

    public static void main (String [] args) {
        setup();
        String imageList = downloadImageList();
        sendMessageForEachURL(imageList);
        startWorkers();
        receiveWorkersData();
        createSummaryFile();
        uploadFiletoS3();
        sendMessageToLocalApp();
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

        /*CreateQueueRequest createQueueRequest = new CreateQueueRequest("ManagerToLocal");
        ManagerToLocalQueue = sqs.createQueue(createQueueRequest).getQueueUrl();
    */
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
        String ManagerToLocalQueueID="ManagerToLocal";
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(ManagerToLocalQueueID);
        String ManagerToLocalQueue=sqs.createQueue(createQueueRequest).getQueueUrl();
        sqs.sendMessage(new SendMessageRequest("ManagerToLocal","done task"));

    }

    private static boolean gotResponse() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest("LocalToManager");
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            if(message.getBody().equals("new task"))
                return true;
        }
        return false;
    }





}
