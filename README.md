# OCR in the Cloud
In this project we have implemneted a real-world application to distributively apply OCR algorithms on images.
we display each image with its recognized text on a webpage.

## Team
We are two students and we implemented the project together.
Our names:

Tal Baum

Stas Radchenko

## Instructions 
There are a few steps we have to follow in order to run the application smoothly.
First ,we have to upload the following zip files to our bucket:

1.manager.zip

2.worker.zip

Second, we will create a bucket through AWS console. in this case we used the bucket "talstas".

Now, we need to run this line in the terminal with the names of our input files and the n we chose.
```
java -jar Ass1.jar inputFileName n
```
## How the program works:
Local Application:
* The localapp creates two queues: LocalToManager and ManagerToLocal.
* The localapp uploads the input file of the amazon reviews to the s3.
* The localapp sends a message to the queue LocalToManager.
(this queue used to send messages between the localApp to the manager.
The local app send a message in this format: 
new task + numOfImagesPerWorker + key (which num of images per worker and key are parsed from local app argumetns).
 * After sending the message the local app is waiting to get the message about the summary-file from manager.
 * When the local app gets this message, it downloads the output files from s3, and create an html file presenting the pics and their ocr text.
 
 Manager:
* open 2 queues Manager2Worker queue and Worker2Manager queue.
1.The manager is "listening" to the "localToManager" queue in busy-wait form. and will wait for new message.
2. when the manager gets a new message he will:
   - download the file from s3
   - parse the arguments from the message -key and num of images per worker.
   - creates a new smaller messages(for each URL in the file) to send to the Manager2Worker queue.
   - after each "numOfImagesPerWorker" he will intiaite and create a worker instance.
   - will count how many messages were sent to the working while using the variable numOfURLs
3. the manager will listen to "Worker2Manager" queue, and for each response he will save the response at allrespones list.
4. after getting enough responses the manage will create the summary file from all the responses.
5. the manager will shut down and closes all the workers instances.
5. The manager will upload the file to s3, and send a message "done task" to local app using ManagerToLocal queue.


