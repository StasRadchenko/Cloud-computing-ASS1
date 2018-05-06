package com.company;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import com.asprise.ocr.Ocr;

import static java.lang.Thread.sleep;

public class Worker {
    private static AWSCredentialsProvider credentialsProvider;
    private static AmazonSQS sqs;
    private static SQSConnectionFactory connectionFactory;
    private static String Worker2Manager;
    private static String Manager2Worker;
    private static String imageURL;
    private static String reciptHandleOfMsg;
    private static String textOfImage;

	public static void main(String [] args) {
		setupWorker();
		while(true){
			if(gotTask()) {
				handleTask();
				sendMsgToManager();
			}
			waitSomeTime();
		}
		
	}
	private static void setupWorker() {
        System.out.println("Welcome to worker ");
        //EC2 PUTTY RUN:
         credentialsProvider = new AWSStaticCredentialsProvider
               (new InstanceProfileCredentialsProvider(false).getCredentials());
        

        //Local run:
       /* credentialsProvider = new AWSStaticCredentialsProvider(
                new EnvironmentVariableCredentialsProvider().getCredentials());*/

        sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        connectionFactory = new SQSConnectionFactory(
                new ProviderConfiguration(),
                AmazonSQSClientBuilder.standard()
                        .withRegion("us-east-1")
                        .withCredentials(credentialsProvider));

        //-------------Worker TO Manaer queues-------------------------
        String WorkerToManagerID="WorkerToManager";
        CreateQueueRequest workQman = new CreateQueueRequest(WorkerToManagerID);
        Worker2Manager=sqs.createQueue(workQman).getQueueUrl();
        //-------------------------------------------------------
        String ManagerToWorkerID="ManagerToWorker";
        CreateQueueRequest manQwork = new CreateQueueRequest(ManagerToWorkerID);
        Manager2Worker=sqs.createQueue(manQwork).getQueueUrl();
        //-------------------------------------------------------
        System.out.println("Setup complete.");
	}

    private static boolean gotTask() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(Manager2Worker);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            System.out.println("GOT MSG FROM Manager TO Worker");
            parseArgumentsFromManager(message);
            return true;
        }
        System.out.println("DIDNT GOT MSG FROM MANAGER TO WORKER");
        return false;
    }

    private static void parseArgumentsFromManager(Message message) {
        String body= message.getBody();
        imageURL = body.substring(body.indexOf('|')+1);
        System.out.println("Requsted URL:" +imageURL);
        reciptHandleOfMsg=message.getReceiptHandle();
        removeImageFromQueue();
    }

    private static void waitSomeTime() {
        try {
            System.out.print("wait loop..");
            sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void handleTask() {
		Ocr.setUp();
		Ocr ocr = new Ocr();
		ocr.startEngine("eng", Ocr.SPEED_FASTEST);
		InputStream in;
		try {
			URL url = new URL(imageURL);
			in=url.openStream();
			Files.copy(in,Paths.get("pic.jpg"),StandardCopyOption.REPLACE_EXISTING);
			in.close();
            textOfImage= ocr.recognize(new File[] { new File ("pic.jpg")}, Ocr.RECOGNIZE_TYPE_ALL, Ocr.OUTPUT_FORMAT_PLAINTEXT);
			System.out.println("Got this text: \n "+ textOfImage);
		} catch (IOException e) {
			e.printStackTrace();
		}
			ocr.stopEngine();
	}

    private static void sendMsgToManager() {
        System.out.println("URL OF QUEUE IN Worker class: " + Worker2Manager);
        System.out.println(imageURL);
        sqs.sendMessage(new SendMessageRequest(Worker2Manager,"done image task" +"|" +imageURL+"|"+textOfImage));
        System.out.println("Messege was sent from worker into the queue");
    }

    private static void removeImageFromQueue() {
        sqs.deleteMessage(new DeleteMessageRequest(Manager2Worker, reciptHandleOfMsg));
        System.out.println("Task was deleted from manager to worker queue!");

    }

}
