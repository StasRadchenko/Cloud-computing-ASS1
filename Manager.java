package com.company;

import static java.lang.Thread.sleep;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import org.apache.commons.codec.binary.Base64;

import javax.jms.JMSException;

public class Manager {
	private static AmazonS3 s3;
    private static AWSCredentialsProvider credentialsProvider;
    private static AmazonEC2 ec2;
    private static AmazonSQS sqs;
    private static List<Instance> instances;
    private static List<Instance> allInstances;
    private static SQSConnectionFactory connectionFactory;
    private static String ManagerToLocalQueue;
    private static String LocalToManagerQueue;
    private static String Manager2Worker;
    private static String Worker2Manager;
    private static int numOfImagesPerWorker;
    private static String key;
    private static String bucketName= "talstas";
    private static int numberOfURLS = 0;
    private static int numberOfResponses=0;
    private static String filename;
    private static LinkedList<String> allResponses;

    public static void main (String [] args) {
        System.out.println("WELCOME to manager");
        //Install manager func
        setup();
        //LIsten for local app to send a message
        while(!gotTaskFromLocal()) {
            waitSomeTime();
        }
        //Got a message from local app
        //Download the list of the images
        downloadImageList();
        //TO TEST:
        //Finished creating workers and sending messages to queue
        List<Message> messages=null;
        while(numberOfResponses<numberOfURLS){
            waitForResponses();
            waitSomeTime();
            System.out.println("URLS: " + numberOfURLS + " Response: " + numberOfResponses);
        }
        kilWorkers();
        createSummaryFile();
        uploadFiletoS3(filename);
        sendMessageToLocalApp();
        closeManager();
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
        ec2 = AmazonEC2ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        connectionFactory = new SQSConnectionFactory(
                new ProviderConfiguration(),
                AmazonSQSClientBuilder.standard()
                        .withRegion("us-east-1")
                        .withCredentials(credentialsProvider));
        //----------Responses list setup
        allResponses = new LinkedList<String>();
        //----------End responses list setup
        String LocalToManagerID="LocalToManager";
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(LocalToManagerID);
         LocalToManagerQueue=sqs.createQueue(createQueueRequest).getQueueUrl();

        CreateQueueRequest createQueueRequest2 = new CreateQueueRequest("ManagerToLocal");
        ManagerToLocalQueue = sqs.createQueue(createQueueRequest2).getQueueUrl();

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

        allInstances=new ArrayList<>();
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
        System.out.println("Input file name: " +parsedArgs[4]);
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
                if (!line.equals("")) {
                    if (weightLoadWorker % numOfImagesPerWorker == 0) {
                        startWorkers();
                    }
                    sqs.sendMessage(new SendMessageRequest(Manager2Worker,"new image task|"+ line));
                    weightLoadWorker++;
                    numberOfURLS++;
                }
            }
			System.out.println("downloaded of image list was completed");
		} catch (IOException e) {
			e.printStackTrace();
		}
        
    }

    private static void startWorkers() {
        try {
            IamInstanceProfileSpecification instanceP=new IamInstanceProfileSpecification();
            instanceP.setArn("arn:aws:iam::644923746621:instance-profile/WorkerRole");
            RunInstancesRequest request = new RunInstancesRequest("ami-1853ac65", 1, 1);
            request.setInstanceType(InstanceType.T2Micro.toString());
            request.setUserData(createWorkerScript());
            request.withSecurityGroups("check");
            request.withKeyName("Talbaum1");
            request.setIamInstanceProfile(instanceP);
            instances = ec2.runInstances(request).getReservation().getInstances();
            System.out.println("Launch instances: " + instances);
            allInstances.addAll(instances);


        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }


    }

    private static String createWorkerScript() {
        StringBuilder managerBuild = new StringBuilder();
        managerBuild.append("#!/bin/bash\n"); //start the bash
        managerBuild.append("sudo su \n");
        managerBuild.append("yum install java-1.8.0 \n");
        managerBuild.append(" y\n");
        managerBuild.append("alternatives --config java \n ");
        managerBuild.append("2\n");
        managerBuild.append("aws s3 cp s3://talstas/worker.zip  worker.zip  \n");
        managerBuild.append("unzip worker.zip\n");
        managerBuild.append("java -jar worker.jar\\n");

        return new String(Base64.encodeBase64(managerBuild.toString().getBytes()));
    }

    private static void kilWorkers() {
        deleteTheQueues();
        closeInstances();
    }

    private static void createSummaryFile() {
        if(allResponses==null)
        {
            System.out.println("No URL to create summary file");
            return;
        }
        try {
            String singleOCR;
            filename="summary.txt";
            PrintWriter writer = new PrintWriter("summary.txt", "UTF-8");
            for(String response: allResponses){
                singleOCR=parseMessage(response);
                writer.println(singleOCR);
                writer.println("-----------------------------------------------------------------");
            }
            System.out.println("Summary flie was created!");
            writer.close();

        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private static String parseMessage(String response) {
        //String body= m.getBody();
        String text = response.substring(response.indexOf('|')+1);
        System.out.println("Messge recivied from worker is: " + text);
        return text;
    }

    private static void uploadFiletoS3(String filename) {
        File summaryFile= new File(filename);
        System.out.println("Manager is uploading the summary file to S3...");
        key = summaryFile.getName().replace('\\', '_').replace('/','_').replace(':', '_');
        PutObjectRequest req = new PutObjectRequest(bucketName, key, summaryFile);
        s3.putObject(req);
    }

    private static void sendMessageToLocalApp() {
        System.out.println("URL OF QUEUE IN MANAGER CLASS: " + ManagerToLocalQueue);
        sqs.sendMessage(new SendMessageRequest(ManagerToLocalQueue,"done task"));
        System.out.println("Messege was sent from manager into the queue");
    }

    private static void closeManager() {
        ec2.shutdown();
        s3.shutdown();
        System.out.println("Manager finished.");
        System.exit(0);
    }
    
    //-----------------------HELPER FUNCTIONS------------------------------------------------------
    private static void waitForResponses() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(Worker2Manager);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
       if(messages.size()>0) {
           for (int i = 0; i < messages.size(); i++) {
               System.out.println("not empty");
               if (messages.get(i).getBody().startsWith("done image task")) {
                   System.out.println("GOT RESPONSE!");
                   allResponses.add(messages.get(i).getBody());
                   String reciptHandleOfMsg = messages.get(i).getReceiptHandle();
                   sqs.deleteMessage(new DeleteMessageRequest(Worker2Manager, reciptHandleOfMsg));
                   numberOfResponses++; // maybe can cancel cas of worker2manager.isEmpty at the while in main
               }
           }
       }
       else
        System.out.println("NO RESPONSE");
        //return messages;
    }
    /*
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(Worker2Manager);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        while (messages.size() < numberOfURLS) {
            waitSomeTime();
            messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        }
        return messages;

    }
*/
    private static void insertPicContentToList(String picContent) {

    }

    private static void waitSomeTime() {
        try {
            System.out.println("wait loop - trying to get all The Workers OCR Text..");
            sleep(8000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void closeInstances() {
        List<String> toCloseList = new ArrayList<>();
        if(allInstances!=null) {
            for (Instance i : allInstances)
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
        try {
            SQSConnection connection = connectionFactory.createConnection();
            AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();
            if (client.queueExists("ManagerToWorker")) {
                System.out.println("ManagerToWorker queue is getting closed!");
                client.getAmazonSQSClient().deleteQueue("ManagerToWorker");
            }
            if (client.queueExists("WorkerToManager")) {
                System.out.println("WorkerToManager queue is getting closed!");
                client.getAmazonSQSClient().deleteQueue("WorkerToManager");
            }
            connection.close();

        } catch (JMSException e) {
            System.out.println("Queue delete error");
        }

    }

   //-----------------------HELPER FUNCTIONS END-----------------------------------------------------
}
