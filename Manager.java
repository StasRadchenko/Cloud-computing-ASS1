package com.company;

import static java.lang.Thread.sleep;

import java.awt.SystemColor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class Manager {
	private static AmazonS3 s3;
    private static AWSCredentialsProvider credentialsProvider;
    private static AmazonSQS sqs;
    private static SQSConnectionFactory connectionFactory;
    private static String ManagerToLocalQueue;
    private static String LocalToManagerQueue;
    private static String Manager2Worker;
    private static String Worker2Manager;
    private static int numOfImagesPerWorker;
    private static String key;
    private static String bucketName= "talstas";
    private static int numberOfURLS = 0;
    private static int numberOfResponses = 0;
    private static LinkedList <String> allResponses;

    public static void main (String [] args) {
        System.out.println("WELCOME to manager");
        //Install manager func
        setup();
        //LIsten for local app to send a message
        while(!gotTaskFromLocal()){
            try {
                System.out.print("wait loop..");
                sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //Got a message from local app
        //Download the list of the images
        downloadImageList();
        //Finished creating workers and sending messages to queue
        while (numberOfResponses < numberOfURLS) {
        	gotResponse();
        	waitSomeTime();
        }
        kilWorkers();
        
        sendMessageToLocalApp();
        receiveWorkersData();
        createSummaryFile();
        uploadFiletoS3();

    }

    private static void kilWorkers() {
		// TODO Auto-generated method stub
		
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
        //START S3
        s3 = AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        //END S3 START
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
        //----------Responses list setup
        allResponses = new LinkedList<String>();
        //----------End responses list setup
        String LocalToManagerID="LocalToManager";
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(LocalToManagerID);
         LocalToManagerQueue=sqs.createQueue(createQueueRequest).getQueueUrl();
        //CREATE QUEUE FOR MANAGER TO WORKERS 
        //-------------MANAGER TO WORKER-------------------------
         String ManagerToWorkerID="ManagerToWorker";
         CreateQueueRequest manQwork = new CreateQueueRequest(ManagerToWorkerID);
         Manager2Worker=sqs.createQueue(manQwork).getQueueUrl();
        //-------------------------------------------------------
        //-------------WORKER TO MANAGER-------------------------
         String WorkerToManagerID="WorkerToManager";
         CreateQueueRequest workQman = new CreateQueueRequest(WorkerToManagerID);
         Worker2Manager=sqs.createQueue(workQman).getQueueUrl();
        //------------------------------------------------------- 
         
        //END QUEUE CREATION 
         
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
    	
        String [] allArgs= msg.toString().split(":");
        String [] args= allArgs[4].split("|");
        String parsedMsg="";
        int delimCounter=0,i=0;
        while(delimCounter<3){
            if(args[i].equals("|")) {
                delimCounter++;
                parsedMsg+= " ";
            }
            else
                parsedMsg+=args[i];
            i++;
        }
        
        String [] parsedArgs= parsedMsg.split(" ");
        System.out.println("THISSS " +parsedArgs[4]);
        numOfImagesPerWorker=Integer.parseInt(parsedArgs[3]);
        key=parsedArgs[4];
    }

    private static void downloadImageList() {
    	com.amazonaws.services.s3.model.S3Object s3obj = s3.getObject(new GetObjectRequest(bucketName, key));
        System.out.println("Downloaded input file, Content-Type is: "  + s3obj.getObjectMetadata().getContentType());
        S3ObjectInputStream objectData = s3obj.getObjectContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s3obj.getObjectContent()));
        String line;
        int weightLoadWorker = 0;
        //Manager reads file, for each line sends to queue if the work load greater, creates new worker 
        try {
			while((line = reader.readLine()) != null) {
				if (weightLoadWorker % numOfImagesPerWorker == 0) {	
					startWorkers();
				}
				sqs.sendMessage(new SendMessageRequest(Manager2Worker,line));
				weightLoadWorker ++;
				numberOfURLS ++ ;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
    }


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
    
    //-----------------------HELPER FUNCTIONS------------------------------------------------------
    private static void gotResponse() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(Worker2Manager);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            System.out.println("not empty");
            if(message.getBody().equals("done image task")) {
             System.out.println("GOT RESPONSE!");
              
             numberOfResponses++;
                
            }
        }
        System.out.println("NO RESPONSE");
    }

    private static void waitSomeTime() {
        try {
            System.out.print("wait loop..");
            sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
   //-----------------------HELPER FUNCTIONS END-----------------------------------------------------
}
