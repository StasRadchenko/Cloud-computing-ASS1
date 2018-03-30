package com.company;
import java.io.File;
import java.util.List;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

public class LocalApplication {
    public static void main (String [] args){
        File imagesURL= new File(args[0]);
        int numOfImagesPerWorker= Integer.parseInt(args[1]);
        setupManager();
        uploadFileToS3(imagesURL);
        sendMsgToSQS();
        checkForResponse();
        downloadResponse(); // maybe should be inside checkForResponse
        close();
    }

    private static void setupManager() {
        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                new EnvironmentVariableCredentialsProvider().getCredentials());
        AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-west-2")
                .build();
        if(!isManagerActive(ec2))
            defineManager();
    }

    private static boolean isManagerActive(AmazonEC2 ec2) {
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

    }

    private static void uploadFileToS3(File imagesURL) {
    }

    private static void sendMsgToSQS() {
    }

    private static void downloadResponse() {
    }

    private static void checkForResponse() {
    }

    private static void close() {
    }

}
